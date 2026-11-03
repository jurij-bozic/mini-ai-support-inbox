package com.example.supportinbox.ai;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;

import com.example.supportinbox.config.AiProperties;
import com.example.supportinbox.message.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Thin OpenRouter client. Logs only operation, model, status and latency; never the
 * API key, message content or provider response bodies.
 */
@Component
public class OpenRouterAiAssistant implements AiAssistant {

	private static final Logger log = LoggerFactory.getLogger(OpenRouterAiAssistant.class);

	private static final int MAX_ATTEMPTS = 2;

	private final RestClient restClient;

	private final AiProperties properties;

	private final JsonMapper mapper;

	private final ClassificationParser parser;

	@Autowired
	public OpenRouterAiAssistant(AiProperties properties, JsonMapper mapper) {
		this(RestClient.builder()
			.baseUrl(properties.baseUrl())
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
			.requestFactory(requestFactory(properties))
			.build(), properties, mapper);
	}

	OpenRouterAiAssistant(RestClient restClient, AiProperties properties, JsonMapper mapper) {
		this.restClient = restClient;
		this.properties = properties;
		this.mapper = mapper;
		this.parser = new ClassificationParser(mapper);
	}

	private static JdkClientHttpRequestFactory requestFactory(AiProperties properties) {
		HttpClient client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
		factory.setReadTimeout(properties.readTimeout());
		return factory;
	}

	@Override
	public ClassificationResult classify(String content) {
		Map<String, Object> body = Map.of(
				"model", properties.classifyModel(),
				"max_tokens", properties.classifyMaxTokens(),
				"temperature", 0,
				"messages", List.of(message("system", Prompts.CLASSIFY_SYSTEM),
						message("user", Prompts.classifyUser(content))),
				"response_format", Map.of("type", "json_schema",
						"json_schema", Map.of("name", "classification", "strict", true,
								"schema", mapper.readTree(Prompts.CLASSIFY_SCHEMA))));
		return parser.parse(complete("classify", properties.classifyModel(), body));
	}

	@Override
	public String draftReply(String content, String customerName, Category category) {
		Map<String, Object> body = Map.of(
				"model", properties.replyModel(),
				"max_tokens", properties.replyMaxTokens(),
				"messages", List.of(message("system", Prompts.REPLY_SYSTEM),
						message("user", Prompts.replyUser(content, customerName, category))));
		String reply = complete("draft", properties.replyModel(), body).trim();
		if (reply.isEmpty()) {
			throw new AiException(AiErrorCode.INVALID_RESPONSE, "Empty reply from provider");
		}
		return reply;
	}

	private static Map<String, String> message(String role, String content) {
		return Map.of("role", role, "content", content);
	}

	/** Sends the request (retrying once on 429/5xx) and returns the first choice's text. */
	private String complete(String operation, String model, Map<String, Object> body) {
		long start = System.nanoTime();
		String payload = mapper.writeValueAsString(body);
		for (int attempt = 1;; attempt++) {
			Response response = send(payload);
			if (isRetryable(response.status()) && attempt < MAX_ATTEMPTS) {
				log.warn("AI {} call to {} returned {}, retrying", operation, model, response.status());
				backoff();
				continue;
			}
			log.info("AI {} call to {} returned {} in {} ms", operation, model, response.status(),
					(System.nanoTime() - start) / 1_000_000);
			return extractContent(response);
		}
	}

	private Response send(String payload) {
		try {
			return restClient.post()
				.uri("/chat/completions")
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.exchange((request, response) -> {
					int status = response.getStatusCode().value();
					// Error bodies are deliberately never read.
					return new Response(status, response.getStatusCode().is2xxSuccessful()
							? response.bodyTo(String.class) : null);
				});
		}
		catch (ResourceAccessException e) {
			if (isTimeout(e)) {
				throw new AiException(AiErrorCode.TIMEOUT, "AI provider timed out", e);
			}
			throw new AiException(AiErrorCode.PROVIDER_ERROR, "AI provider unreachable", e);
		}
	}

	private String extractContent(Response response) {
		int status = response.status();
		if (status == 401) {
			throw new AiException(AiErrorCode.AUTH_ERROR, "AI provider rejected credentials");
		}
		if (status == 402) {
			throw new AiException(AiErrorCode.INSUFFICIENT_CREDITS, "AI provider reports insufficient credits");
		}
		if (status == 429) {
			throw new AiException(AiErrorCode.RATE_LIMITED, "AI provider rate limit hit");
		}
		if (status < 200 || status >= 300) {
			throw new AiException(AiErrorCode.PROVIDER_ERROR, "AI provider returned HTTP " + status);
		}
		try {
			JsonNode content = mapper.readTree(response.body()).path("choices").path(0).path("message")
				.path("content");
			if (content.isString()) {
				return content.asString();
			}
		}
		catch (JacksonException e) {
			throw new AiException(AiErrorCode.INVALID_RESPONSE, "AI provider returned malformed JSON", e);
		}
		throw new AiException(AiErrorCode.INVALID_RESPONSE, "AI provider response had no message content");
	}

	private static boolean isRetryable(int status) {
		return status == 429 || status >= 500;
	}

	private static boolean isTimeout(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) {
				return true;
			}
		}
		return false;
	}

	private void backoff() {
		try {
			Thread.sleep(properties.retryBackoff());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AiException(AiErrorCode.PROVIDER_ERROR, "Interrupted while waiting to retry", e);
		}
	}

	private record Response(int status, String body) {
	}

}
