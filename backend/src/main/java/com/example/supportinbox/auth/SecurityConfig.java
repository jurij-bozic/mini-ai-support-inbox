package com.example.supportinbox.auth;

import com.example.supportinbox.common.RateLimitFilter;
import com.example.supportinbox.config.AdminProperties;
import com.example.supportinbox.config.RateLimitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailAuthEntryPoint entryPoint,
			RateLimitProperties rateLimit, ObjectMapper objectMapper) throws Exception {
		return http
			// CSRF protection defends against browsers attaching ambient credentials (cookies) to
			// forged requests. This API is stateless and uses no cookies: credentials travel only in
			// an Authorization header set explicitly by the frontend, so there is nothing to forge.
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.anyRequest().authenticated())
			.addFilterAfter(new RateLimitFilter(rateLimit, objectMapper), AuthorizationFilter.class)
			.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	UserDetailsService userDetailsService(AdminProperties admin) {
		return new InMemoryUserDetailsManager(
				User.withUsername(admin.username()).password(admin.passwordHash()).roles("ADMIN").build());
	}

}
