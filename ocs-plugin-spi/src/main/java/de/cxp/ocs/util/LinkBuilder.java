package de.cxp.ocs.util;

/**
 * Interface for internal utility function exposed to the provided plugins. The link builder is immutable and
 * <strong>always</strong> uses the 'current URL state' as a basis. So each call just returns a copy state reflected as
 * an URL and does not change the linkbuilder itself.
 */
public interface LinkBuilder {

	/**
	 * Returns an URL with the specified filter(s) set. If that filter already exists, the values will be merged in case
	 * the mergeValues option is set in the configuration. Otherwise the values are replaced.
	 * 
	 * @param filtername
	 *        name of the filter to add
	 * @param mergeValues
	 *        set true to merge values in case of existing filter, to replace existing values set to false
	 * @param values
	 *        the values the filter should have
	 * @return the created URL
	 */
	String withFilterAsLink(String filtername, boolean mergeValues, String... values);

	/**
	 * <p>
	 * Returns an URL with that filter set.
	 * If the filter-name existed before, it will be replaced completely.
	 * No multi-select value merging will be done.
	 * </p>
	 * 
	 * @param filtername
	 *        name of the filter to add
	 * @param values
	 *        the values the filter should have
	 * @return the created filter URL
	 */
	default String withExactFilterAsLink(String filtername, String... values) {
		return withFilterAsLink(filtername, false, values);
	}

	/**
	 * Returns an URL without the filter parameter of that according facet.
	 * 
	 * @param filtername
	 *        the filter to remove from the current ulr.
	 * @return
	 */
	String withoutFilterAsLink(String filtername);
	
}
