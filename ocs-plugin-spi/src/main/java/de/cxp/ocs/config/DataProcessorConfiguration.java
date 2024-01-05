package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration wrapper for which Document-Pre/Post-Processors to be used and
 * custom settings for them.
 */
@NoArgsConstructor
@Data
public class DataProcessorConfiguration {

	/**
	 * List of activated processors.
	 */
	private final List<String>						processors		= new ArrayList<>();

	/**
	 * <p>
	 * Settings for the single document pre/post processors. As a key the full
	 * canonical class name of the according processor is expected. The value is
	 * a custom string-to-string map with arbitrary settings for a processor.
	 * </p>
	 * <p>
	 * Check the java-doc of the available processors for more details.
	 * </p>
	 */
	private final Map<String, Map<String, String>>	configuration	= new LinkedHashMap<>();

	private boolean useDefaultConfig;
}
