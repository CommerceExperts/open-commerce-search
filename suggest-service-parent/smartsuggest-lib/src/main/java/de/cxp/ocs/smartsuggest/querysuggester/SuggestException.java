package de.cxp.ocs.smartsuggest.querysuggester;

import java.io.Serial;

public class SuggestException extends RuntimeException {

	@Serial private static final long serialVersionUID = -4055077488050336590L;

	public SuggestException(String message, Throwable cause) {
		super(message, cause);
	}

	public SuggestException(Throwable cause) {
		super(cause);
	}
}
