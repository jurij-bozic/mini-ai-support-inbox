package com.example.supportinbox.common;

import java.io.IOException;
import java.time.Duration;
import java.util.regex.Pattern;

import com.example.supportinbox.config.RateLimitProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Caps the endpoints that trigger paid LLM calls ({@code POST /api/messages} and
 * {@code POST /api/messages/{id}/regenerate}) with one global in-memory token bucket.
 * Filters run before Spring MVC, so the 429 {@link ProblemDetail} is written here rather than
 * by the controller advice. It is registered in the security chain (not as a servlet filter)
 * so that unauthenticated requests are rejected with 401 before they can use up tokens.
 */
public class RateLimitFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

	private static final Pattern AI_ENDPOINT = Pattern.compile("/api/messages(/[^/]+/regenerate)?");

	private final Bucket bucket;

	private final ObjectMapper objectMapper;

	public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.bucket = Bucket.builder()
			.addLimit(limit -> limit.capacity(properties.aiPerMinute())
				.refillGreedy(properties.aiPerMinute(), Duration.ofMinutes(1)))
			.build();
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !HttpMethod.POST.matches(request.getMethod()) || !AI_ENDPOINT.matcher(request.getRequestURI()).matches();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (probe.isConsumed()) {
			chain.doFilter(request, response);
			return;
		}
		long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).plusMillis(999).toSeconds());
		log.warn("AI rate limit exceeded; retry after {}s", retryAfterSeconds);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
				"Too many AI requests. Try again in " + retryAfterSeconds + " seconds.");
		problem.setTitle("Too Many Requests");
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), problem);
	}

}
