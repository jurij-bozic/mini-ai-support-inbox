package com.example.supportinbox.auth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Answers unauthenticated requests with a 401 {@link ProblemDetail}. It deliberately omits the
 * {@code WWW-Authenticate} header so browsers do not show their native Basic auth dialog.
 * Security filters run before Spring MVC, so this cannot be handled by the controller advice.
 */
@Component
public class ProblemDetailAuthEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	public ProblemDetailAuthEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
				"Authentication is required to access this resource.");
		problem.setTitle("Unauthorized");
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), problem);
	}

}
