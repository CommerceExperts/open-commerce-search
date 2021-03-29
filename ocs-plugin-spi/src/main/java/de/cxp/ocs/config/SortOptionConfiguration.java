package de.cxp.ocs.config;

import de.cxp.ocs.model.result.SortOrder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional configuration that overwrites the default way to present and handle
 * sortings.
 */
@Data
@NoArgsConstructor
public class SortOptionConfiguration {

	/**
	 * reference field for which this sort configuration applies.
	 */
	String field;

	/**
	 * specify which sort order options should be returned in the result. If
	 * empty, sorting options won't be part of result.
	 */
	SortOrder[] shownOrders = new SortOrder[] { SortOrder.ASC, SortOrder.DESC };

	/**
	 * From
	 * https://www.elastic.co/guide/en/elasticsearch/reference/master/sort-search-results.html#_missing_values:
	 * <p>
	 * <blockquote>
	 * The missing parameter specifies how docs which are missing the sort field
	 * should be treated: The missing value can be set to _last, _first, or a
	 * custom value (that will be used for missing docs as the sort value).
	 * </blockquote>
	 * </p>
	 * <p>
	 * <strong>
	 * Other then the Elasticsearch default, the default in OCS is "0".
	 * </strong>
	 * </p>
	 */
	String missing = "0";

}
