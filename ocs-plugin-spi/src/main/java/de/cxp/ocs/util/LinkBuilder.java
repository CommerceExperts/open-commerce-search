package de.cxp.ocs.util;

/**
 * Interface for internal utility function exposed to the provided plugins.
 */
public interface LinkBuilder {

	/**
	 * Returns an URL with the specified filter(s) set. If that filter already exists, the values will be merged in case
	 * the mergeValues option is set in the configuration. Otherwise the values are replaced.
	 * 
	 * @param filterConfig
	 * @param values
	 * @return
	 */
	String withFilterAsLink(String filtername, boolean mergeValues, String... values);

	/**
	 * <p>
	 * Returns an URL with that filter set.
	 * If the filter-name existed before, it will be replaced completely.
	 * No multi-select value merging will be done.
	 * </p>
	 * 
	 * @param facetConfig
	 * @param filterInputValues
	 * @return
	 */
	default String withExactFilterAsLink(String filtername, String... values) {
		return withFilterAsLink(filtername, false, values);
	}

	/**
	 * Returns an URL without the filter parameter of that according facet.
	 * 
	 * @param facetConfig
	 * @return
	 */
	String withoutFilterAsLink(String filtername);
	
}
