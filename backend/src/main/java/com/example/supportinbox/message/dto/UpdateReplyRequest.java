package com.example.supportinbox.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for both saving an edited reply and approving it.
 */
public record UpdateReplyRequest(
		@NotBlank @Size(max = 5000) String finalReply,
		@NotNull Long version) {
}
