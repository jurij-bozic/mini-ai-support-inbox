package com.example.supportinbox.auth;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	public record MeResponse(String username) {
	}

	@GetMapping("/me")
	public MeResponse me(Principal principal) {
		return new MeResponse(principal.getName());
	}

}
