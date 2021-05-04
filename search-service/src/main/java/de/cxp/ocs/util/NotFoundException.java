package de.cxp.ocs.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class NotFoundException extends Exception {

	public NotFoundException(String entityName) {
		super(entityName + " not found");
	}
}
