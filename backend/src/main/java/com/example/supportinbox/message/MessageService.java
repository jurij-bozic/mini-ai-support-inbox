package com.example.supportinbox.message;

import java.util.UUID;

import com.example.supportinbox.ai.AiAssistant;
import com.example.supportinbox.ai.AiErrorCode;
import com.example.supportinbox.ai.AiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the message lifecycle. Deliberately not transactional: database steps
 * go through {@link MessageTransactions}, so no transaction spans an AI call.
 * Message content is never logged.
 */
@Service
public class MessageService {

	private static final Logger log = LoggerFactory.getLogger(MessageService.class);

	private final MessageTransactions transactions;

	private final AiAssistant aiAssistant;

	MessageService(MessageTransactions transactions, AiAssistant aiAssistant) {
		this.transactions = transactions;
		this.aiAssistant = aiAssistant;
	}

	/**
	 * Saves the message, then classifies and drafts. An AI failure still returns the
	 * saved message, in status AI_FAILED.
	 */
	public Message create(String content, String customerName, String customerEmail) {
		Message pending = transactions.savePending(content, customerName, customerEmail);
		log.info("Message {} created, status {}", pending.getId(), pending.getStatus());
		return processWithAi(pending);
	}

	public Message get(UUID id) {
		return transactions.get(id);
	}

	public Message editReply(UUID id, long version, String finalReply) {
		Message message = transactions.editReply(id, version, requireText(finalReply));
		log.info("Message {} reply edited", id);
		return message;
	}

	public Message approve(UUID id, long version, String finalReply) {
		Message message = transactions.approve(id, version, requireText(finalReply));
		log.info("Message {} approved", id);
		return message;
	}

	/**
	 * Re-runs classification and drafting. Allowed from AI_FAILED and DRAFT_READY;
	 * a new draft replaces the agent's edited reply.
	 */
	public Message regenerate(UUID id) {
		Message pending = transactions.beginRegeneration(id);
		log.info("Message {} regeneration started", id);
		return processWithAi(pending);
	}

	private Message processWithAi(Message pending) {
		UUID id = pending.getId();
		long start = System.nanoTime();
		Category category;
		String draft;
		try {
			category = aiAssistant.classify(pending.getContent()).category();
			draft = aiAssistant.draftReply(pending.getContent(), pending.getCustomerName(), category);
			if (draft == null || draft.isBlank()) {
				throw new AiException(AiErrorCode.INVALID_RESPONSE, "Empty draft");
			}
		}
		catch (AiException e) {
			return fail(id, e.getCode(), start);
		}
		catch (RuntimeException e) {
			log.error("Unexpected error during AI processing of message {}", id, e);
			return fail(id, AiErrorCode.PROVIDER_ERROR, start);
		}
		Message ready = transactions.markDraftReady(id, category, draft);
		log.info("Message {} DRAFT_READY, category {}, AI took {} ms", id, category, elapsedMillis(start));
		return ready;
	}

	private Message fail(UUID id, AiErrorCode code, long start) {
		Message failed = transactions.markAiFailed(id, code.name());
		log.warn("Message {} AI_FAILED, error {}, AI took {} ms", id, code, elapsedMillis(start));
		return failed;
	}

	private static long elapsedMillis(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}

	private static String requireText(String reply) {
		if (reply == null || reply.isBlank()) {
			throw new IllegalArgumentException("Reply must not be blank");
		}
		return reply;
	}

}
