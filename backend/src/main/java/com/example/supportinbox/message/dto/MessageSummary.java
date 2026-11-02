package com.example.supportinbox.message.dto;

import java.time.Instant;
import java.util.UUID;

import com.example.supportinbox.message.Category;
import com.example.supportinbox.message.Message;
import com.example.supportinbox.message.MessageStatus;

public record MessageSummary(
		UUID id,
		String customerName,
		String customerEmail,
		String contentPreview,
		Category category,
		MessageStatus status,
		Instant createdAt) {

	private static final int PREVIEW_LENGTH = 120;

	public static MessageSummary from(Message message) {
		String content = message.getContent();
		String preview = content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH) + "…";
		return new MessageSummary(message.getId(), message.getCustomerName(), message.getCustomerEmail(), preview,
				message.getCategory(), message.getStatus(), message.getCreatedAt());
	}

}
