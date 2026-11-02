package com.example.supportinbox.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.supportinbox.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MessageRepositoryTest {

	@Autowired
	private MessageRepository repository;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void savesAndLoadsMessage() {
		Message saved = repository.saveAndFlush(Message.create("Where is my order?", "Ana", "ana@example.com"));

		Message loaded = repository.findById(saved.getId()).orElseThrow();

		assertThat(loaded.getId()).isNotNull();
		assertThat(loaded.getContent()).isEqualTo("Where is my order?");
		assertThat(loaded.getCustomerName()).isEqualTo("Ana");
		assertThat(loaded.getCustomerEmail()).isEqualTo("ana@example.com");
		assertThat(loaded.getStatus()).isEqualTo(MessageStatus.PENDING_AI);
		assertThat(loaded.getCategory()).isNull();
		assertThat(loaded.getCreatedAt()).isNotNull();
		assertThat(loaded.getUpdatedAt()).isNotNull();
		assertThat(loaded.getVersion()).isZero();
	}

	@Test
	void incrementsVersionOnUpdate() {
		Message message = repository.saveAndFlush(Message.create("Hello", null, null));

		ReflectionTestUtils.setField(message, "aiDraft", "Draft");
		Message updated = repository.saveAndFlush(message);

		assertThat(updated.getVersion()).isEqualTo(1L);
		assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getCreatedAt());
	}

	@Test
	void rejectsInvalidStatus() {
		assertThatThrownBy(() -> insert("hello", "BOGUS", null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsInvalidCategory() {
		assertThatThrownBy(() -> insert("hello", "PENDING_AI", "BOGUS"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsEmptyContent() {
		assertThatThrownBy(() -> insert("", "PENDING_AI", null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsOverlongContent() {
		assertThatThrownBy(() -> insert("x".repeat(5001), "PENDING_AI", null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void acceptsContentAtMaxLength() {
		insert("x".repeat(5000), "PENDING_AI", null);

		assertThat(repository.count()).isEqualTo(1);
	}

	private void insert(String content, String status, String category) {
		jdbc.update("INSERT INTO messages (content, status, category) VALUES (?, ?, ?)", content, status, category);
	}

}
