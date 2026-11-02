package com.example.supportinbox.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.supportinbox.TestcontainersConfiguration;
import com.example.supportinbox.ai.AiAssistant;
import com.example.supportinbox.ai.AiErrorCode;
import com.example.supportinbox.ai.AiException;
import com.example.supportinbox.ai.ClassificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MessageApiIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private MessageRepository repository;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AiAssistant ai;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		when(ai.classify(anyString())).thenReturn(new ClassificationResult(Category.QUESTION));
		when(ai.draftReply(anyString(), any(), any())).thenReturn("AI draft");
	}

	@Test
	void createEditApproveFlow() throws Exception {
		JsonNode created = create("Where is my order?");
		String id = created.get("id").asText();
		assertThat(created.get("status").asText()).isEqualTo("DRAFT_READY");

		MvcResult edited = mvc.perform(put("/api/messages/{id}/reply", id).contentType(MediaType.APPLICATION_JSON)
			.content(reply("Edited reply", created.get("version").asLong())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT_READY"))
			.andExpect(jsonPath("$.finalReply").value("Edited reply"))
			.andExpect(jsonPath("$.aiDraft").value("AI draft"))
			.andReturn();
		long version = read(edited).get("version").asLong();

		mvc.perform(post("/api/messages/{id}/approve", id).contentType(MediaType.APPLICATION_JSON)
			.content(reply("Final reply", version)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.finalReply").value("Final reply"))
			.andExpect(jsonPath("$.aiDraft").value("AI draft"))
			.andExpect(jsonPath("$.approvedAt").exists());

		mvc.perform(get("/api/messages/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
	}

	@Test
	void createReturns201WithAiFailedStatusWhenAiFails() throws Exception {
		when(ai.classify(anyString())).thenThrow(new AiException(AiErrorCode.TIMEOUT, "timeout"));

		mvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON).content(createBody("Help")))
			.andExpect(status().isCreated())
			.andExpect(header().exists("Location"))
			.andExpect(jsonPath("$.status").value("AI_FAILED"))
			.andExpect(jsonPath("$.aiErrorCode").value("TIMEOUT"));
	}

	@Test
	void regenerateRecoversFromAiFailure() throws Exception {
		when(ai.classify(anyString())).thenThrow(new AiException(AiErrorCode.TIMEOUT, "timeout"));
		String id = create("Help").get("id").asText();
		doReturn(new ClassificationResult(Category.COMPLAINT)).when(ai).classify(anyString());

		mvc.perform(post("/api/messages/{id}/regenerate", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT_READY"))
			.andExpect(jsonPath("$.category").value("COMPLAINT"))
			.andExpect(jsonPath("$.aiErrorCode").doesNotExist());
	}

	@Test
	void validationErrorsListOffendingFields() throws Exception {
		mvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON)
			.content("{\"content\":\"\",\"customerEmail\":\"not-an-email\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='content')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field=='customerEmail')]").exists());

		mvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON)
			.content(createBody("x".repeat(5001))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("content"));
	}

	@Test
	void malformedBodyIsBadRequest() throws Exception {
		mvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON).content("{nope"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.title").value("Bad request"));
	}

	@Test
	void unknownIdIs404() throws Exception {
		mvc.perform(get("/api/messages/{id}", "00000000-0000-0000-0000-000000000000"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void malformedIdIs400() throws Exception {
		mvc.perform(get("/api/messages/{id}", "not-a-uuid")).andExpect(status().isBadRequest());
	}

	@Test
	void editAfterApprovalIs409() throws Exception {
		JsonNode created = create("Question?");
		String id = created.get("id").asText();
		MvcResult approved = mvc.perform(post("/api/messages/{id}/approve", id).contentType(MediaType.APPLICATION_JSON)
			.content(reply("Done", created.get("version").asLong()))).andExpect(status().isOk()).andReturn();
		long version = read(approved).get("version").asLong();

		mvc.perform(put("/api/messages/{id}/reply", id).contentType(MediaType.APPLICATION_JSON)
			.content(reply("Again", version)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.title").value("Invalid status transition"));
		mvc.perform(post("/api/messages/{id}/regenerate", id)).andExpect(status().isConflict());
	}

	@Test
	void staleVersionIs409() throws Exception {
		JsonNode created = create("Question?");
		String id = created.get("id").asText();
		long version = created.get("version").asLong();
		mvc.perform(put("/api/messages/{id}/reply", id).contentType(MediaType.APPLICATION_JSON)
			.content(reply("First edit", version))).andExpect(status().isOk());

		mvc.perform(put("/api/messages/{id}/reply", id).contentType(MediaType.APPLICATION_JSON)
			.content(reply("Second edit", version)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.title").value("Version conflict"));
		mvc.perform(post("/api/messages/{id}/approve", id).contentType(MediaType.APPLICATION_JSON)
			.content(reply("Approve stale", version))).andExpect(status().isConflict());
	}

	@Test
	void blankReplyAndMissingVersionAreBadRequests() throws Exception {
		String id = create("Question?").get("id").asText();

		mvc.perform(put("/api/messages/{id}/reply", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"finalReply\":\"  \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='finalReply')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field=='version')]").exists());
	}

	@Test
	void listFiltersPaginatesAndSortsNewestFirst() throws Exception {
		create("First?");
		when(ai.classify(anyString())).thenReturn(new ClassificationResult(Category.PRAISE));
		create("Second, thanks");
		when(ai.classify(anyString())).thenThrow(new AiException(AiErrorCode.PROVIDER_ERROR, "boom"));
		create("Third");

		mvc.perform(get("/api/messages"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.items[0].contentPreview").value("Third"))
			.andExpect(jsonPath("$.items[2].contentPreview").value("First?"));
		mvc.perform(get("/api/messages").param("status", "AI_FAILED"))
			.andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(get("/api/messages").param("category", "PRAISE"))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].contentPreview").value("Second, thanks"));
		mvc.perform(get("/api/messages").param("status", "DRAFT_READY").param("category", "QUESTION"))
			.andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(get("/api/messages").param("size", "2").param("page", "1"))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void invalidListParametersAreBadRequests() throws Exception {
		mvc.perform(get("/api/messages").param("size", "101")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/messages").param("size", "0")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/messages").param("page", "-1")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/messages").param("status", "NOPE")).andExpect(status().isBadRequest());
	}

	private JsonNode create(String content) throws Exception {
		MvcResult result = mvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON).content(createBody(content)))
			.andExpect(status().isCreated())
			.andReturn();
		return read(result);
	}

	private JsonNode read(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private String createBody(String content) {
		return objectMapper.createObjectNode().put("content", content).toString();
	}

	private String reply(String text, long version) {
		return objectMapper.createObjectNode().put("finalReply", text).put("version", version).toString();
	}

}
