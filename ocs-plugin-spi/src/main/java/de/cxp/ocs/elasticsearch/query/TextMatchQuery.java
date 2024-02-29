package de.cxp.ocs.elasticsearch.query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * A carrier of search queries on master and variant level. Both will be
 * combined into a final query that includes filters and all the rest.
 */
@Getter
@AllArgsConstructor
public class TextMatchQuery<Q> {

	public TextMatchQuery(Q masterQuery, Q variantQuery, boolean isWithSpellCorrection, boolean acceptNoResult) {
		this(masterQuery, variantQuery, isWithSpellCorrection, acceptNoResult, null);
	}

	@Setter
	private Q masterLevelQuery;

	private Q variantLevelQuery;

	/**
	 * Set to 'true' if the according queries already consider spelling errors,
	 * e.g. because a fuzzy search is done anyways.
	 */
	private boolean isWithSpellCorrection = false;

	// TODO: do this with configuration so it's not necessary to decide at the
	// builder level
	private boolean acceptNoResult = false;

	private String queryDescription;

}
