package de.cxp.ocs.spi.search;

import java.util.Map;

/**
 * Can be used to modify the user query prior it is processed by the
 * UserQueryAnalyzer, for example to normalize the query.
 */
public interface UserQueryPreprocessor extends ConfigurableExtension {

	String preProcess(String userQuery);

	/**
	 * Extended preProcess method that also get the meta-data map passed that can be filled with relevant meta data.
	 * That meta data will then be part of the search response.
	 * 
	 * @param userQuery
	 * @param resultMetaData
	 * @return
	 */
	default String preProcess(String userQuery, Map<String, Object> resultMetaData) {
		return preProcess(userQuery);
	}

}
