package com.example.supportinbox.ai;

import com.example.supportinbox.message.Category;

/**
 * The only AI abstraction the message package depends on.
 * Implementations signal failures with {@link AiException}.
 */
public interface AiAssistant {

	ClassificationResult classify(String content);

	/**
	 * @param customerName may be null; the draft then uses a neutral greeting
	 * @return plain-text reply body only
	 */
	String draftReply(String content, String customerName, Category category);

}
