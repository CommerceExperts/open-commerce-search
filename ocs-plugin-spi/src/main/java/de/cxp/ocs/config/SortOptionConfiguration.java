package de.cxp.ocs.config;

import de.cxp.ocs.model.result.SortOrder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Optional configuration that overwrites the default way to present and handle
 * sortings.
 */
@Getter // write setters with java-doc!
@NoArgsConstructor
public class SortOptionConfiguration {

	String field;

	String label;

	SortOrder order = SortOrder.ASC;

	String missing = "0";

	/**
	 * reference field for which this sort configuration applies.
	 * 
	 * @param field
	 *        the field to set
	 * @return self
	 */
	public SortOptionConfiguration setField(String field) {
		this.field = field;
		return this;
	}


	/**
	 * Display label for the according sort option. Should be unique across all
	 * the sort options.
	 * 
	 * @param label
	 *        the label to set
	 * @return self
	 */
	public SortOptionConfiguration setLabel(String label) {
		this.label = label;
		return this;
	}


	/**
	 * Specify the sort order of that configured option. If null, this sort
	 * option will not be part of the result (but you could also skip the
	 * configuration of this option at all to achive that)
	 * 
	 * @param order
	 *        the order to set
	 * @return self
	 */
	public SortOptionConfiguration setOrder(SortOrder order) {
		this.order = order;
		return this;
	}


	/**
	 * From <a href=
	 * "https://www.elastic.co/guide/en/elasticsearch/reference/master/sort-search-results.html#_missing_values">Elasticsearch
	 * documentation</a>:
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
	 * 
	 * @param missing
	 *        the missing to set
	 * @return self
	 */
	public SortOptionConfiguration setMissing(String missing) {
		this.missing = missing;
		return this;
	}

}
