package com.example.supportinbox.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limit for the endpoints that trigger LLM calls, shared by all callers (it caps cost, not per-user use).
 */
@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(@Positive int aiPerMinute) {

}
