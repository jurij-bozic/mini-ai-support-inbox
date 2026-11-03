package com.example.supportinbox.common;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.supportinbox.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = "app.rate-limit.ai-per-minute=2")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitIntegrationTest {

	private static final RequestPostProcessor ADMIN = httpBasic("test-admin", "test-password");

	@Autowired
	private MockMvc mvc;

	@Test
	void aiEndpointsAreLimitedGloballyAndOtherEndpointsAreNot() throws Exception {
		String id = createMessage().andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
		id = id.substring(id.lastIndexOf('/') + 1);

		// second token goes to regenerate: the bucket is shared by both AI endpoints
		mvc.perform(post("/api/messages/{id}/regenerate", id).with(ADMIN)).andExpect(status().isOk());

		createMessage().andExpect(status().isTooManyRequests())
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER))
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
			.andExpect(jsonPath("$.status").value(429))
			.andExpect(jsonPath("$.title").value("Too Many Requests"));
		mvc.perform(post("/api/messages/{id}/regenerate", id).with(ADMIN)).andExpect(status().isTooManyRequests());

		mvc.perform(get("/api/messages").with(ADMIN)).andExpect(status().isOk());
		mvc.perform(get("/api/messages/{id}", id).with(ADMIN)).andExpect(status().isOk());
	}

	private org.springframework.test.web.servlet.ResultActions createMessage() throws Exception {
		return mvc.perform(post("/api/messages").with(ADMIN)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"content\":\"Where is my order?\"}"));
	}

}
