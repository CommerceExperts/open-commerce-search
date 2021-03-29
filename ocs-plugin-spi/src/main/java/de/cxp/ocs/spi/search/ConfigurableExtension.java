package de.cxp.ocs.spi.search;

import java.util.Map;

/**
 * super-interface for all extension interfaces that optionally can accepts
 * custom settings.
 */
public interface ConfigurableExtension {

	default void initialize(Map<String, String> settings) {}

}
