package de.cxp.ocs.elasticsearch.query;

import java.util.List;

import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;

/**
 * A query builder that receives the analyzed user query to build a proper
 * Elasticsearch query.
 * (actually a QueryBuilder - so this should be a QueryBuilderBuilder :)).
 */
public interface ESQueryBuilder {

	MasterVariantQuery buildQuery(List<QueryStringTerm> searchWords);

	boolean allowParallelSpellcheckExecution();

	void setName(String name);

	String getName();
}
