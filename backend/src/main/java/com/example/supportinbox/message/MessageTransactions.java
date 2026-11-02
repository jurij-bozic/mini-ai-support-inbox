package com.example.supportinbox.message;

import java.util.UUID;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short database steps of the message lifecycle. Kept in a separate bean from
 * {@link MessageService} so each call is a real transaction (no self-invocation)
 * and no transaction is ever open during an AI call.
 */
@Service
class MessageTransactions {

	private final MessageRepository repository;

	MessageTransactions(MessageRepository repository) {
		this.repository = repository;
	}

	@Transactional
	Message savePending(String content, String customerName, String customerEmail) {
		return repository.save(Message.create(content, customerName, customerEmail));
	}

	@Transactional(readOnly = true)
	Message get(UUID id) {
		return find(id);
	}

	@Transactional
	Message markDraftReady(UUID id, Category category, String draft) {
		Message message = find(id);
		message.markDraftReady(category, draft);
		return message;
	}

	@Transactional
	Message markAiFailed(UUID id, String errorCode) {
		Message message = find(id);
		message.markAiFailed(errorCode);
		return message;
	}

	@Transactional
	Message beginRegeneration(UUID id) {
		Message message = find(id);
		message.beginRegeneration();
		return message;
	}

	@Transactional
	Message editReply(UUID id, long version, String reply) {
		Message message = find(id);
		requireVersion(message, version);
		message.editReply(reply);
		return message;
	}

	@Transactional
	Message approve(UUID id, long version, String reply) {
		Message message = find(id);
		requireVersion(message, version);
		message.approve(reply);
		return message;
	}

	private Message find(UUID id) {
		return repository.findById(id).orElseThrow(() -> new MessageNotFoundException(id));
	}

	private void requireVersion(Message message, long expectedVersion) {
		if (!message.getVersion().equals(expectedVersion)) {
			throw new ObjectOptimisticLockingFailureException(Message.class, message.getId());
		}
	}

}
