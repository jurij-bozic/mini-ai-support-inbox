package com.example.supportinbox;

import java.util.Locale;

import com.example.supportinbox.ai.AiAssistant;
import com.example.supportinbox.ai.ClassificationResult;
import com.example.supportinbox.message.Category;

/**
 * Deterministic stand-in for the OpenRouter client; tests must never call the real LLM API.
 */
public class StubAiAssistant implements AiAssistant {

	@Override
	public ClassificationResult classify(String content) {
		String text = content.toLowerCase(Locale.ROOT);
		Category category = Category.OTHER;
		if (text.contains("?")) {
			category = Category.QUESTION;
		}
		else if (text.contains("thank")) {
			category = Category.PRAISE;
		}
		else if (text.contains("broken") || text.contains("complain") || text.contains("unacceptable")) {
			category = Category.COMPLAINT;
		}
		return new ClassificationResult(category);
	}

	@Override
	public String draftReply(String content, String customerName, Category category) {
		String greeting = customerName == null || customerName.isBlank() ? "Hello," : "Hello " + customerName + ",";
		return greeting + "\n\nThank you for your message. Our team will review it and follow up with you shortly.\n\n"
				+ "Kind regards,\nSupport Team";
	}

}
