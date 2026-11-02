package com.example.supportinbox.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MessageTest {

	private static Message pending() {
		return Message.create("Where is my order?", "Ana", null);
	}

	private static Message draftReady() {
		Message message = pending();
		message.markDraftReady(Category.QUESTION, "AI draft");
		return message;
	}

	private static Message aiFailed() {
		Message message = pending();
		message.markAiFailed("TIMEOUT");
		return message;
	}

	private static Message approved() {
		Message message = draftReady();
		message.approve("Final");
		return message;
	}

	@Test
	void markDraftReadyStoresCategoryAndDraftAndPrefillsReply() {
		Message message = pending();

		message.markDraftReady(Category.QUESTION, "AI draft");

		assertThat(message.getStatus()).isEqualTo(MessageStatus.DRAFT_READY);
		assertThat(message.getCategory()).isEqualTo(Category.QUESTION);
		assertThat(message.getAiDraft()).isEqualTo("AI draft");
		assertThat(message.getFinalReply()).isEqualTo("AI draft");
	}

	@Test
	void markAiFailedStoresErrorCode() {
		Message message = aiFailed();

		assertThat(message.getStatus()).isEqualTo(MessageStatus.AI_FAILED);
		assertThat(message.getAiErrorCode()).isEqualTo("TIMEOUT");
	}

	@Test
	void editReplyKeepsAiDraftAndStatus() {
		Message message = draftReady();

		message.editReply("Edited");

		assertThat(message.getStatus()).isEqualTo(MessageStatus.DRAFT_READY);
		assertThat(message.getFinalReply()).isEqualTo("Edited");
		assertThat(message.getAiDraft()).isEqualTo("AI draft");
	}

	@Test
	void approveSetsFinalReplyAndTimestamp() {
		Message message = draftReady();

		message.approve("Final");

		assertThat(message.getStatus()).isEqualTo(MessageStatus.APPROVED);
		assertThat(message.getFinalReply()).isEqualTo("Final");
		assertThat(message.getAiDraft()).isEqualTo("AI draft");
		assertThat(message.getApprovedAt()).isNotNull();
	}

	@Test
	void beginRegenerationFromAiFailedClearsErrorCode() {
		Message message = aiFailed();

		message.beginRegeneration();

		assertThat(message.getStatus()).isEqualTo(MessageStatus.PENDING_AI);
		assertThat(message.getAiErrorCode()).isNull();
	}

	@Test
	void regenerationFromDraftReadyReplacesDraftAndEditedReply() {
		Message message = draftReady();
		message.editReply("Edited");

		message.beginRegeneration();
		message.markDraftReady(Category.COMPLAINT, "New draft");

		assertThat(message.getStatus()).isEqualTo(MessageStatus.DRAFT_READY);
		assertThat(message.getCategory()).isEqualTo(Category.COMPLAINT);
		assertThat(message.getAiDraft()).isEqualTo("New draft");
		assertThat(message.getFinalReply()).isEqualTo("New draft");
	}

	@Test
	void rejectsMarkDraftReadyUnlessPending() {
		assertInvalid(draftReady(), m -> m.markDraftReady(Category.OTHER, "x"));
		assertInvalid(aiFailed(), m -> m.markDraftReady(Category.OTHER, "x"));
		assertInvalid(approved(), m -> m.markDraftReady(Category.OTHER, "x"));
	}

	@Test
	void rejectsMarkAiFailedUnlessPending() {
		assertInvalid(draftReady(), m -> m.markAiFailed("TIMEOUT"));
		assertInvalid(aiFailed(), m -> m.markAiFailed("TIMEOUT"));
		assertInvalid(approved(), m -> m.markAiFailed("TIMEOUT"));
	}

	@Test
	void rejectsRegenerationFromPendingAndApproved() {
		assertInvalid(pending(), Message::beginRegeneration);
		assertInvalid(approved(), Message::beginRegeneration);
	}

	@Test
	void rejectsEditUnlessDraftReady() {
		assertInvalid(pending(), m -> m.editReply("x"));
		assertInvalid(aiFailed(), m -> m.editReply("x"));
		assertInvalid(approved(), m -> m.editReply("x"));
	}

	@Test
	void rejectsApproveUnlessDraftReady() {
		assertInvalid(pending(), m -> m.approve("x"));
		assertInvalid(aiFailed(), m -> m.approve("x"));
		assertInvalid(approved(), m -> m.approve("x"));
	}

	private static void assertInvalid(Message message, java.util.function.Consumer<Message> action) {
		MessageStatus before = message.getStatus();
		assertThatThrownBy(() -> action.accept(message)).isInstanceOf(InvalidStatusTransitionException.class);
		assertThat(message.getStatus()).isEqualTo(before);
	}

}
