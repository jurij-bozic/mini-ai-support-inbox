package com.example.supportinbox.common;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.example.supportinbox.message.MessageController;
import com.example.supportinbox.message.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MessageController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private MessageService service;

	@Test
	void unexpectedExceptionReturns500WithoutInternalDetails() throws Exception {
		when(service.get(UUID.fromString("00000000-0000-0000-0000-000000000001")))
			.thenThrow(new IllegalStateException("secret internal detail"));

		mvc.perform(get("/api/messages/{id}", "00000000-0000-0000-0000-000000000001"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.title").value("Internal server error"))
			.andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
	}

}
