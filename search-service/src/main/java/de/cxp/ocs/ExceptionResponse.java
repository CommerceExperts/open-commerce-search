package de.cxp.ocs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class ExceptionResponse {

	String	message;
	int		code;

	/**
	 * Unique ID of the error that is also logged to the server log output.
	 */
	String errorId;

	Exception exception;

	final long timestamp = System.currentTimeMillis();
}
