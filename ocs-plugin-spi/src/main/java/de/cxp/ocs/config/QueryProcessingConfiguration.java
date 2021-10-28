package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.List;

import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import de.cxp.ocs.spi.search.UserQueryPreprocessor;
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
	 *        list of full canonical {@link UserQueryPreprocessor}
	 *        implementation class names
	 * @return self
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
	 *        full canonical {@link UserQueryAnalyzer} implementation class name
	 * @return self
	 */
	public QueryProcessingConfiguration setUserQueryAnalyzer(String userQueryAnalyzer) {
		this.userQueryAnalyzer = userQueryAnalyzer;
		return this;
	}

}
