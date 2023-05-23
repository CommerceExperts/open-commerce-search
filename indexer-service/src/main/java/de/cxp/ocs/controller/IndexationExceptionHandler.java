package de.cxp.ocs.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.ElasticsearchStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerAdvice
public class IndexationExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(
			value = { ExecutionException.class, IOException.class, UncheckedIOException.class,
					RuntimeException.class, ClassNotFoundException.class })
	public ResponseEntity<String> handleInternalErrors(Exception e) {
		final String errorId = UUID.randomUUID().toString();
		log.error("Internal Server Error " + errorId, e);
		return new ResponseEntity<>("Something went wrong. Error reference: " + errorId,
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@ExceptionHandler({ IllegalArgumentException.class })
	public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.toString());
	}

	@ExceptionHandler({ ElasticsearchStatusException.class })
	public ResponseEntity<String> handleElasticsearchStatusExceptions(ElasticsearchStatusException e) {
		return ResponseEntity.status(e.status().getStatus()).body(e.toString());
	}
}
