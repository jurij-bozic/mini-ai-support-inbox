package com.example.supportinbox.message;

import java.util.UUID;

public class MessageNotFoundException extends RuntimeException {

	public MessageNotFoundException(UUID id) {
		super("Message " + id + " not found");
	}

}
