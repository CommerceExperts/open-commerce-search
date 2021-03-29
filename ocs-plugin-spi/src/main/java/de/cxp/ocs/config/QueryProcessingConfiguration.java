package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class QueryProcessingConfiguration {

	/**
	 * <p>
	 * List of custom query preprocessors (their canonical or simple class name)
	 * to be activated for the usage at the associated tenant.
	 * </p>
	 * <p>
	 * Processors that can't be found, will be ignored (with some warning log
	 * message).
	 * </p>
	 */
	private List<String> userQueryPreprocessors = new ArrayList<>(0);

	/**
	 * <p>
	 * Optional classname (canonical or simple) of the userQueryAnalyzer to use.
	 * </p>
	 * <p>
	 * Besides the standard available analyzers (WhitespaceAnalyzer and
	 * WhitespaceWithShingles) there could also be custom analyzers.
	 * </p>
	 * <p>
	 * If nothing is defined here or the specified one is not found, then the
	 * default analyzer (WhitespaceAnalyzer) is used.
	 * </p>
	 */
	private String userQueryAnalyzer = null;

}
