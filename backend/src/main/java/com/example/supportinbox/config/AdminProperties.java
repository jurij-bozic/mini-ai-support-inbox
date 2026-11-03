package com.example.supportinbox.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The single admin account. {@code passwordHash} is a Spring Security encoded value such as
 * {@code {bcrypt}$2a$...}, never a plaintext password.
 */
@Validated
@ConfigurationProperties("app.admin")
public record AdminProperties(@NotBlank String username, @NotBlank String passwordHash) {
}
