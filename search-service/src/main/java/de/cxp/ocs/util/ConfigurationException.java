package de.cxp.ocs.util;


/**
 * A internal exception because of invalid configuration. Requires a message.
 * Should never be use for a response, just for logging.
 */
public class ConfigurationException extends Exception {

	public ConfigurationException(String message) {
		super(message);
	}
}
