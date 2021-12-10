package de.cxp.ocs.util;


/**
 * A internal exception because of invalid configuration. Requires a message.
 * Should never be use for a response, just for logging.
 */
public class ConfigurationException extends Exception {

	private static final long serialVersionUID = 930904517583581836L;

	public ConfigurationException(String message) {
		super(message);
	}
}
