package de.cxp.ocs.spi.search;

/**
 * Can be used to modify the user query prior it is processed by the
 * UserQueryAnalyzer, for example to normalize the query.
 */
public interface UserQueryPreprocessor extends ConfigurableExtension {

	String preProcess(String userQuery);

}
