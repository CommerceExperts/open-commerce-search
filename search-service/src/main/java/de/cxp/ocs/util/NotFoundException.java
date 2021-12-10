package de.cxp.ocs.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class NotFoundException extends Exception {

	private static final long serialVersionUID = 7968636297702034034L;

	public NotFoundException(String entityName) {
		super(entityName + " not found");
	}
}
