package com.example.supportinbox.common;

import java.util.List;
import java.util.Map;

import com.example.supportinbox.message.InvalidStatusTransitionException;
import com.example.supportinbox.message.MessageNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every error to an RFC 9457 {@link ProblemDetail}. Extends
 * {@link ResponseEntityExceptionHandler} so framework exceptions (405, 415, ...) use the same format.
 * Client-facing details are written here and never taken from unexpected exceptions.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
			.toList();
		return ResponseEntity.badRequest().body(validationProblem(errors));
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = ex.getParameterValidationResults()
			.stream()
			.flatMap(result -> result.getResolvableErrors()
				.stream()
				.map(error -> Map.of("field", result.getMethodParameter().getParameterName(), "message",
						String.valueOf(error.getDefaultMessage()))))
			.toList();
		return ResponseEntity.badRequest().body(validationProblem(errors));
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(
			HttpMessageNotReadableException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return ResponseEntity.badRequest()
			.body(problem(HttpStatus.BAD_REQUEST, "Bad request", "Request body is missing or malformed."));
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(
			MissingServletRequestParameterException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return ResponseEntity.badRequest()
			.body(problem(HttpStatus.BAD_REQUEST, "Bad request", "Missing parameter: " + ex.getParameterName()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Bad request", "Invalid value for parameter: " + ex.getName());
	}

	@ExceptionHandler(MessageNotFoundException.class)
	ProblemDetail handleNotFound(MessageNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
	}

	@ExceptionHandler(InvalidStatusTransitionException.class)
	ProblemDetail handleInvalidTransition(InvalidStatusTransitionException ex) {
		return problem(HttpStatus.CONFLICT, "Invalid status transition", ex.getMessage());
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
		return problem(HttpStatus.CONFLICT, "Version conflict",
				"The message was changed elsewhere. Reload it and try again.");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred.");
	}

	private static ProblemDetail validationProblem(List<Map<String, String>> errors) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
				"One or more fields are invalid.");
		problem.setProperty("errors", errors);
		return problem;
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
