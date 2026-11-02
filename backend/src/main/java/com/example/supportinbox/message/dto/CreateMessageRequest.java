package com.example.supportinbox.message.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMessageRequest(
		@NotBlank @Size(max = 5000) String content,
		@Size(max = 200) String customerName,
		@Email @Size(max = 320) String customerEmail) {
}
