package com.example.supportinbox.ai;

/**
 * Failure of an AI call. The message must never contain raw provider error bodies.
 */
public class AiException extends RuntimeException {

	private final AiErrorCode code;

	public AiException(AiErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public AiException(AiErrorCode code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public AiErrorCode getCode() {
		return code;
	}

}
