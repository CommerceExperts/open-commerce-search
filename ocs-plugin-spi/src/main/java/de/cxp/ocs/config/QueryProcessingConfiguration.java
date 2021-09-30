package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

@Getter // write setters with java-doc!
public class QueryProcessingConfiguration {

	private List<String> userQueryPreprocessors = new ArrayList<>(0);

	private String userQueryAnalyzer = null;

	/**
	 * <p>
	 * List of custom query preprocessors (their canonical or simple class name)
	 * to be activated for the usage at the associated tenant.
	 * </p>
	 * <p>
	 * Processors that can't be found, will be ignored (with some warning log
	 * message).
	 * </p>
	 * 
	 * @param userQueryPreprocessors
	 * @return config again for fluent access
	 */
	public QueryProcessingConfiguration setUserQueryPreprocessors(List<String> userQueryPreprocessors) {
		this.userQueryPreprocessors = userQueryPreprocessors;
		return this;
	}

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
	 * 
	 * @param userQueryAnalyzer
	 * @return config again for fluent access
	 */
	public QueryProcessingConfiguration setUserQueryAnalyzer(String userQueryAnalyzer) {
		this.userQueryAnalyzer = userQueryAnalyzer;
		return this;
	}

}
