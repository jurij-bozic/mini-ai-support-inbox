package com.example.supportinbox.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the OpenRouter (OpenAI-compatible) chat completions API.
 * {@code apiKey} is a secret: it must never be logged or included in responses.
 */
@Validated
@ConfigurationProperties("app.ai")
public record AiProperties(
		@NotBlank String baseUrl,
		@NotBlank String apiKey,
		@NotBlank String classifyModel,
		@NotBlank String replyModel,
		@NotNull Duration connectTimeout,
		@NotNull Duration readTimeout,
		@NotNull Duration retryBackoff,
		@Positive int classifyMaxTokens,
		@Positive int replyMaxTokens) {

	@Override
	public String toString() {
		return "AiProperties[baseUrl=" + baseUrl + ", classifyModel=" + classifyModel + ", replyModel=" + replyModel
				+ ", apiKey=***]";
	}

}
