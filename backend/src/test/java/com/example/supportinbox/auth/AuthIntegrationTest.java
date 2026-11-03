package com.example.supportinbox.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

	private static final String ALLOWED_ORIGIN = "http://allowed.example";

	@Autowired
	private MockMvc mvc;

	@Test
	void missingCredentialsReturn401ProblemDetailWithoutChallengeHeader() throws Exception {
		mvc.perform(get("/api/messages"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.title").value("Unauthorized"));
	}

	@Test
	void wrongPasswordReturns401WithoutChallengeHeader() throws Exception {
		mvc.perform(get("/api/messages").with(httpBasic("test-admin", "wrong")))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
			.andExpect(jsonPath("$.title").value("Unauthorized"));
	}

	@Test
	void unknownUserReturns401() throws Exception {
		mvc.perform(get("/api/auth/me").with(httpBasic("someone-else", "test-password")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void validCredentialsAccessApi() throws Exception {
		mvc.perform(get("/api/messages").with(httpBasic("test-admin", "test-password")))
			.andExpect(status().isOk());
	}

	@Test
	void meReturnsUsername() throws Exception {
		mvc.perform(get("/api/auth/me").with(httpBasic("test-admin", "test-password")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("test-admin"));
	}

	@Test
	void healthIsPublic() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void preflightFromAllowedOriginIsAcceptedWithoutCredentials() throws Exception {
		mvc.perform(options("/api/messages").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization, Content-Type"));
	}

	@Test
	void preflightFromDisallowedOriginIsRejected() throws Exception {
		mvc.perform(options("/api/messages").header(HttpHeaders.ORIGIN, "http://evil.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

}
