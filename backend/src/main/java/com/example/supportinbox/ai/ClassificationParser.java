package com.example.supportinbox.ai;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.supportinbox.message.Category;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lenient parser for the classification model output. Anything unusable falls back to
 * {@link Category#OTHER}; the caller is told via {@link ClassificationResult#fellBack()}.
 */
final class ClassificationParser {

	private static final Pattern FENCE = Pattern.compile("^```[a-zA-Z]*\\s*(.*?)\\s*```$", Pattern.DOTALL);

	private final JsonMapper mapper;

	ClassificationParser(JsonMapper mapper) {
		this.mapper = mapper;
	}

	ClassificationResult parse(String raw) {
		String text = raw == null ? "" : raw.trim();
		Matcher fence = FENCE.matcher(text);
		if (fence.matches()) {
			text = fence.group(1).trim();
		}
		try {
			JsonNode value = mapper.readTree(text).path("category");
			if (value.isString()) {
				return new ClassificationResult(Category.valueOf(value.asString().trim().toUpperCase(Locale.ROOT)));
			}
		}
		catch (JacksonException | IllegalArgumentException e) {
			// fall through to the fallback below
		}
		return new ClassificationResult(Category.OTHER, true);
	}

}
