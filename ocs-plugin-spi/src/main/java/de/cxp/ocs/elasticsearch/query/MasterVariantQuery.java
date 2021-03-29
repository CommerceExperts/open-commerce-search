package de.cxp.ocs.elasticsearch.query;

import org.elasticsearch.index.query.QueryBuilder;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A carrier of Elasticsearch queries on master and variant level. Both will be
 * combined into a final query that includes filters and all the rest.
 */
@Getter
@AllArgsConstructor
public class MasterVariantQuery {

	private QueryBuilder masterLevelQuery;

	private QueryBuilder variantLevelQuery;

	/**
	 * Set to 'true' if the according queries already consider spelling errors,
	 * e.g. because a fuzzy search is done anyways.
	 */
	private boolean isWithSpellCorrection = false;

	// TODO: do this with configuration so it's not necessary to decide at the
	// builder level
	private boolean acceptNoResult = false;
}
