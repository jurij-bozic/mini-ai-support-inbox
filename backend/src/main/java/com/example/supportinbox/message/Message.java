package com.example.supportinbox.message;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "messages")
public class Message {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "customer_name")
	private String customerName;

	@Column(name = "customer_email")
	private String customerEmail;

	@Column(nullable = false)
	private String content;

	@Enumerated(EnumType.STRING)
	private Category category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MessageStatus status;

	@Column(name = "ai_draft")
	private String aiDraft;

	@Column(name = "final_reply")
	private String finalReply;

	@Column(name = "ai_error_code")
	private String aiErrorCode;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "approved_at")
	private Instant approvedAt;

	@Version
	private Long version;

	protected Message() {
	}

	public static Message create(String content, String customerName, String customerEmail) {
		Message message = new Message();
		message.content = content;
		message.customerName = customerName;
		message.customerEmail = customerEmail;
		message.status = MessageStatus.PENDING_AI;
		return message;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getCustomerName() {
		return customerName;
	}

	public String getCustomerEmail() {
		return customerEmail;
	}

	public String getContent() {
		return content;
	}

	public Category getCategory() {
		return category;
	}

	public MessageStatus getStatus() {
		return status;
	}

	public String getAiDraft() {
		return aiDraft;
	}

	public String getFinalReply() {
		return finalReply;
	}

	public String getAiErrorCode() {
		return aiErrorCode;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getApprovedAt() {
		return approvedAt;
	}

	public Long getVersion() {
		return version;
	}

}
