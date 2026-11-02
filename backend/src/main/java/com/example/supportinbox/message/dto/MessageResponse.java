package com.example.supportinbox.message.dto;

import java.time.Instant;
import java.util.UUID;

import com.example.supportinbox.message.Category;
import com.example.supportinbox.message.Message;
import com.example.supportinbox.message.MessageStatus;

public record MessageResponse(
		UUID id,
		String customerName,
		String customerEmail,
		String content,
		Category category,
		MessageStatus status,
		String aiDraft,
		String finalReply,
		String aiErrorCode,
		Instant createdAt,
		Instant updatedAt,
		Instant approvedAt,
		Long version) {

	public static MessageResponse from(Message message) {
		return new MessageResponse(message.getId(), message.getCustomerName(), message.getCustomerEmail(),
				message.getContent(), message.getCategory(), message.getStatus(), message.getAiDraft(),
				message.getFinalReply(), message.getAiErrorCode(), message.getCreatedAt(), message.getUpdatedAt(),
				message.getApprovedAt(), message.getVersion());
	}

}
