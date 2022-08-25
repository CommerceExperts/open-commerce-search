package de.cxp.ocs.model.params;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class ArrangedSearchQuery extends FilteredSearchQuery {

	/**
	 * One or more sets of documents/products that should be placed at the top
	 * of the result. They will be returned in separate result slices, but they
	 * will be excluded from the organic user search result.
	 */
	public ProductSet[] arrangedProductSets;

	/**
	 * Per default the "natural unarranged results" defined by the included query+filters are part of the response. If
	 * those are not required, set this flag to 'false'.
	 */
	public boolean includeMainResult = true;
}
