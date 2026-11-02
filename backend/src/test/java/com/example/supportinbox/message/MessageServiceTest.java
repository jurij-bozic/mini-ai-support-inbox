package com.example.supportinbox.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.example.supportinbox.ai.AiAssistant;
import com.example.supportinbox.ai.AiErrorCode;
import com.example.supportinbox.ai.AiException;
import com.example.supportinbox.ai.ClassificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageServiceTest {

	@Mock
	private MessageRepository repository;

	@Mock
	private AiAssistant ai;

	private final Map<UUID, Message> store = new HashMap<>();

	private MessageService service;

	@BeforeEach
	void setUp() {
		when(repository.save(any(Message.class))).thenAnswer(invocation -> {
			Message message = invocation.getArgument(0);
			if (message.getId() == null) {
				ReflectionTestUtils.setField(message, "id", UUID.randomUUID());
				ReflectionTestUtils.setField(message, "version", 0L);
			}
			store.put(message.getId(), message);
			return message;
		});
		when(repository.findById(any(UUID.class))).thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
		when(ai.classify(anyString())).thenReturn(new ClassificationResult(Category.QUESTION));
		when(ai.draftReply(anyString(), any(), any())).thenReturn("AI draft");
		service = new MessageService(new MessageTransactions(repository), ai);
	}

	private Message created() {
		return service.create("Where is my order?", "Ana", null);
	}

	private Message failed() {
		doThrow(new AiException(AiErrorCode.TIMEOUT, "timeout")).when(ai).classify(anyString());
		Message message = created();
		doReturn(new ClassificationResult(Category.QUESTION)).when(ai).classify(anyString());
		return message;
	}

	@Test
	void createHappyPathStoresCategoryAndDraft() {
		Message message = created();

		assertThat(message.getStatus()).isEqualTo(MessageStatus.DRAFT_READY);
		assertThat(message.getCategory()).isEqualTo(Category.QUESTION);
		assertThat(message.getAiDraft()).isEqualTo("AI draft");
		assertThat(message.getFinalReply()).isEqualTo("AI draft");
		verify(ai).draftReply("Where is my order?", "Ana", Category.QUESTION);
	}

	@Test
	void classificationFailureSavesAiFailedWithoutDrafting() {
		when(ai.classify(anyString())).thenThrow(new AiException(AiErrorCode.RATE_LIMITED, "limited"));

		Message message = created();

		assertThat(message.getStatus()).isEqualTo(MessageStatus.AI_FAILED);
		assertThat(message.getAiErrorCode()).isEqualTo("RATE_LIMITED");
		assertThat(message.getAiDraft()).isNull();
		verify(ai, never()).draftReply(anyString(), any(), any());
	}

	@Test
	void draftFailureSavesAiFailed() {
		when(ai.draftReply(anyString(), any(), any())).thenThrow(new AiException(AiErrorCode.TIMEOUT, "timeout"));

		Message message = created();

		assertThat(message.getStatus()).isEqualTo(MessageStatus.AI_FAILED);
		assertThat(message.getAiErrorCode()).isEqualTo("TIMEOUT");
		assertThat(message.getAiDraft()).isNull();
	}

	@Test
	void blankDraftSavesAiFailedWithInvalidResponse() {
		when(ai.draftReply(anyString(), any(), any())).thenReturn("  ");

		Message message = created();

		assertThat(message.getStatus()).isEqualTo(MessageStatus.AI_FAILED);
		assertThat(message.getAiErrorCode()).isEqualTo("INVALID_RESPONSE");
	}

	@Test
	void unexpectedAiErrorSavesAiFailedWithProviderError() {
		when(ai.classify(anyString())).thenThrow(new IllegalStateException("boom"));

		Message message = created();

		assertThat(message.getStatus()).isEqualTo(MessageStatus.AI_FAILED);
		assertThat(message.getAiErrorCode()).isEqualTo("PROVIDER_ERROR");
	}

	@Test
	void editReplyKeepsAiDraft() {
		Message message = created();

		Message edited = service.editReply(message.getId(), message.getVersion(), "Edited");

		assertThat(edited.getStatus()).isEqualTo(MessageStatus.DRAFT_READY);
		assertThat(edited.getFinalReply()).isEqualTo("Edited");
		assertThat(edited.getAiDraft()).isEqualTo("AI draft");
	}

	@Test
	void approveMakesMessageFinal() {
		Message message = created();

		Message approved = service.approve(message.getId(), message.getVersion(), "Final");

		assertThat(approved.getStatus()).isEqualTo(MessageStatus.APPROVED);
		assertThat(approved.getFinalReply()).isEqualTo("Final");
		assertThat(approved.getApprovedAt()).isNotNull();
	}

	@Test
	void editAndApproveRejectedAfterApproval() {
		Message message = created();
		service.approve(message.getId(), message.getVersion(), "Final");

		assertThatThrownBy(() -> service.editReply(message.getId(), message.getVersion(), "x"))
				.isInstanceOf(InvalidStatusTransitionException.class);
		assertThatThrownBy(() -> service.approve(message.getId(), message.getVersion(), "x"))
				.isInstanceOf(InvalidStatusTransitionException.class);
		assertThatThrownBy(() -> service.regenerate(message.getId()))
				.isInstanceOf(InvalidStatusTransitionException.class);
	}

	@Test
	void editAndApproveRejectedFromAiFailed() {
		Message message = failed();

		assertThatThrownBy(() -> service.editReply(message.getId(), message.getVersion(), "x"))
				.isInstanceOf(InvalidStatusTransitionException.class);
		assertThatThrownBy(() -> service.approve(message.getId(), message.getVersion(), "x"))
				.isInstanceOf(InvalidStatusTransitionException.class);
	}

	@Test
	void editAndApproveRejectedWhilePending() {
		Message message = created();
		ReflectionTestUtils.setField(message, "status", MessageStatus.PENDING_AI);

		assertThatThrownBy(() -> service.editReply(message.getId(), message.getVersion(), "x"))
				.isInstanceOf(InvalidStatusTransitionException.class);
		assertThatThrownBy(() -> service.approve(message.getId(), message.getVersion(), "x"))
				.isInstanceOf(InvalidStatusTransitionException.class);
		assertThatThrownBy(() -> service.regenerate(message.getId()))
				.isInstanceOf(InvalidStatusTransitionException.class);
	}

	@Test
	void staleVersionIsRejected() {
		Message message = created();

		assertThatThrownBy(() -> service.editReply(message.getId(), message.getVersion() + 1, "x"))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThatThrownBy(() -> service.approve(message.getId(), message.getVersion() + 1, "x"))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	@Test
	void blankReplyIsRejected() {
		Message message = created();

		assertThatThrownBy(() -> service.editReply(message.getId(), message.getVersion(), " "))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.approve(message.getId(), message.getVersion(), null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void regenerateFromAiFailedProducesDraft() {
		Message message = failed();

		Message regenerated = service.regenerate(message.getId());

		assertThat(regenerated.getStatus()).isEqualTo(MessageStatus.DRAFT_READY);
		assertThat(regenerated.getAiErrorCode()).isNull();
		assertThat(regenerated.getCategory()).isEqualTo(Category.QUESTION);
		assertThat(regenerated.getAiDraft()).isEqualTo("AI draft");
	}

	@Test
	void regenerateFromDraftReadyReclassifiesAndReplacesEditedReply() {
		Message message = created();
		service.editReply(message.getId(), message.getVersion(), "Edited");
		doReturn(new ClassificationResult(Category.COMPLAINT)).when(ai).classify(anyString());
		doReturn("New draft").when(ai).draftReply(anyString(), any(), any());

		Message regenerated = service.regenerate(message.getId());

		assertThat(regenerated.getStatus()).isEqualTo(MessageStatus.DRAFT_READY);
		assertThat(regenerated.getCategory()).isEqualTo(Category.COMPLAINT);
		assertThat(regenerated.getAiDraft()).isEqualTo("New draft");
		assertThat(regenerated.getFinalReply()).isEqualTo("New draft");
	}

	@Test
	void regenerateThatFailsAgainEndsInAiFailed() {
		Message message = failed();
		doThrow(new AiException(AiErrorCode.PROVIDER_ERROR, "down")).when(ai).classify(anyString());

		Message regenerated = service.regenerate(message.getId());

		assertThat(regenerated.getStatus()).isEqualTo(MessageStatus.AI_FAILED);
		assertThat(regenerated.getAiErrorCode()).isEqualTo("PROVIDER_ERROR");
	}

	@Test
	void unknownIdThrowsNotFound() {
		UUID id = UUID.randomUUID();

		assertThatThrownBy(() -> service.get(id)).isInstanceOf(MessageNotFoundException.class);
		assertThatThrownBy(() -> service.regenerate(id)).isInstanceOf(MessageNotFoundException.class);
	}

}
