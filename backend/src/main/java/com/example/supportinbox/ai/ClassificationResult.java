package com.example.supportinbox.ai;

import com.example.supportinbox.message.Category;

/**
 * @param fellBack true when the model output was unusable and {@code category} is the
 * OTHER fallback, so the caller can log it together with the message ID
 */
public record ClassificationResult(Category category, boolean fellBack) {

	public ClassificationResult(Category category) {
		this(category, false);
	}

}
