package com.example.supportinbox.message;

public class InvalidStatusTransitionException extends RuntimeException {

	public InvalidStatusTransitionException(MessageStatus current, String action) {
		super("Cannot " + action + " a message in status " + current);
	}

}
