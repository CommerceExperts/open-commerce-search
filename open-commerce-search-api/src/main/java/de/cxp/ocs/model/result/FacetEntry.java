package de.cxp.ocs.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FacetEntry {

	/**
	 * Associated filter key.
	 */
	public String key;

	/**
	 * Estimated amount of documents that will be returned, if this facet entry
	 * is picked as filter.
	 */
	public long docCount;

	/**
	 * URL conform query parameters, that has to be used to
	 * filter the result.
	 */
	public String link;

}
