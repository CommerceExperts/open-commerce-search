package de.cxp.ocs.smartsuggest.querysuggester;

/**
 *
 */
public class SuggestException extends RuntimeException {

	private static final long serialVersionUID = -4055077488050336590L;

	public SuggestException(String message) {
		super(message);
	}

	public SuggestException(String message, Throwable cause) {
		super(message, cause);
	}

	public SuggestException(Throwable cause) {
		super(cause);
	}
}
