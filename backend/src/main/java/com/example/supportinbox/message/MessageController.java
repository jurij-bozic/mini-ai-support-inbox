package com.example.supportinbox.message;

import java.net.URI;
import java.util.UUID;

import com.example.supportinbox.message.dto.CreateMessageRequest;
import com.example.supportinbox.message.dto.MessageResponse;
import com.example.supportinbox.message.dto.MessageSummary;
import com.example.supportinbox.message.dto.PageResponse;
import com.example.supportinbox.message.dto.UpdateReplyRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

	private final MessageService service;

	MessageController(MessageService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<MessageResponse> create(@Valid @RequestBody CreateMessageRequest request) {
		Message message = service.create(request.content(), blankToNull(request.customerName()),
				blankToNull(request.customerEmail()));
		return ResponseEntity.created(URI.create("/api/messages/" + message.getId()))
			.body(MessageResponse.from(message));
	}

	@GetMapping
	PageResponse<MessageSummary> list(@RequestParam(required = false) MessageStatus status,
			@RequestParam(required = false) Category category, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(service.list(status, category, page, size), MessageSummary::from);
	}

	@GetMapping("/{id}")
	MessageResponse get(@PathVariable UUID id) {
		return MessageResponse.from(service.get(id));
	}

	@PutMapping("/{id}/reply")
	MessageResponse editReply(@PathVariable UUID id, @Valid @RequestBody UpdateReplyRequest request) {
		return MessageResponse.from(service.editReply(id, request.version(), request.finalReply()));
	}

	@PostMapping("/{id}/approve")
	MessageResponse approve(@PathVariable UUID id, @Valid @RequestBody UpdateReplyRequest request) {
		return MessageResponse.from(service.approve(id, request.version(), request.finalReply()));
	}

	@PostMapping("/{id}/regenerate")
	MessageResponse regenerate(@PathVariable UUID id) {
		return MessageResponse.from(service.regenerate(id));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
