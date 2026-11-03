package com.example.supportinbox.message;

import java.time.Duration;
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

	/**
	 * Longer than the worst-case AI run: two calls, each with 5 s connect, 15 s read and one
	 * retry, is roughly 85 s.
	 */
	public static final Duration PENDING_STALE_AFTER = Duration.ofMinutes(2);

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

	// Status transitions. All lifecycle rules live here.

	public void markDraftReady(Category category, String draft) {
		requireStatus("complete AI processing of", MessageStatus.PENDING_AI);
		this.category = category;
		this.aiDraft = draft;
		this.finalReply = draft;
		this.aiErrorCode = null;
		this.status = MessageStatus.DRAFT_READY;
	}

	public void markAiFailed(String errorCode) {
		requireStatus("fail AI processing of", MessageStatus.PENDING_AI);
		this.aiErrorCode = errorCode;
		this.status = MessageStatus.AI_FAILED;
	}

	/**
	 * Regenerate is allowed from AI_FAILED, DRAFT_READY, and from a PENDING_AI message that is
	 * stuck (see {@link #isStuck}), e.g. because the backend restarted mid-processing.
	 */
	public void beginRegeneration(Instant now) {
		if (!canRegenerate(now)) {
			throw new InvalidStatusTransitionException(status, "regenerate");
		}
		this.aiErrorCode = null;
		this.status = MessageStatus.PENDING_AI;
	}

	public boolean canRegenerate(Instant now) {
		return status == MessageStatus.AI_FAILED || status == MessageStatus.DRAFT_READY || isStuck(now);
	}

	/**
	 * A PENDING_AI message not updated for {@link #PENDING_STALE_AFTER} can no longer be
	 * processing: it is well beyond the longest possible AI run.
	 */
	public boolean isStuck(Instant now) {
		return status == MessageStatus.PENDING_AI && updatedAt != null
				&& updatedAt.plus(PENDING_STALE_AFTER).isBefore(now);
	}

	public void editReply(String reply) {
		requireStatus("edit the reply of", MessageStatus.DRAFT_READY);
		this.finalReply = reply;
	}

	public void approve(String reply) {
		requireStatus("approve", MessageStatus.DRAFT_READY);
		this.finalReply = reply;
		this.approvedAt = Instant.now();
		this.status = MessageStatus.APPROVED;
	}

	private void requireStatus(String action, MessageStatus... allowed) {
		for (MessageStatus candidate : allowed) {
			if (status == candidate) {
				return;
			}
		}
		throw new InvalidStatusTransitionException(status, action);
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
