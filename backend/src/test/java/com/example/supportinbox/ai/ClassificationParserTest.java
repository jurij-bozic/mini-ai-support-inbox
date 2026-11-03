package com.example.supportinbox.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.supportinbox.message.Category;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class ClassificationParserTest {

	private final ClassificationParser parser = new ClassificationParser(JsonMapper.builder().build());

	@Test
	void parsesPlainJson() {
		ClassificationResult result = parser.parse("{\"category\": \"COMPLAINT\"}");

		assertThat(result.category()).isEqualTo(Category.COMPLAINT);
		assertThat(result.fellBack()).isFalse();
	}

	@Test
	void stripsMarkdownFences() {
		assertThat(parser.parse("```json\n{\"category\": \"PRAISE\"}\n```").category()).isEqualTo(Category.PRAISE);
		assertThat(parser.parse("```\n{\"category\": \"QUESTION\"}\n```").category()).isEqualTo(Category.QUESTION);
	}

	@Test
	void trimsAndUppercasesValue() {
		ClassificationResult result = parser.parse("  {\"category\": \" question \"}\n");

		assertThat(result.category()).isEqualTo(Category.QUESTION);
		assertThat(result.fellBack()).isFalse();
	}

	@Test
	void ignoresExtraFields() {
		assertThat(parser.parse("{\"reason\": \"thanks\", \"category\": \"PRAISE\"}").category())
			.isEqualTo(Category.PRAISE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "not json", "{\"category\": ", "{}", "{\"category\": null}", "{\"category\": 3}",
			"{\"category\": \"SPAM\"}", "[\"QUESTION\"]", "QUESTION" })
	void unusableOutputFallsBackToOther(String raw) {
		ClassificationResult result = parser.parse(raw);

		assertThat(result.category()).isEqualTo(Category.OTHER);
		assertThat(result.fellBack()).isTrue();
	}

	@Test
	void nullFallsBackToOther() {
		assertThat(parser.parse(null)).isEqualTo(new ClassificationResult(Category.OTHER, true));
	}

}
