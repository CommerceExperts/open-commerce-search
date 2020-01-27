package de.cxp.ocs.elasticsearch.query.builder;

import java.util.List;

import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;

/**
 * a query builder that only receives the user query to build a fancy
 * Elasticsearch query
 * (actually a QueryBuilder - so this should be a QueryBuilderBuilder :)).
 */
public interface ESQueryBuilder {

	MasterVariantQuery buildQuery(List<QueryStringTerm> searchWords);

	boolean allowParallelSpellcheckExecution();

	void setName(String name);

	String getName();
}
