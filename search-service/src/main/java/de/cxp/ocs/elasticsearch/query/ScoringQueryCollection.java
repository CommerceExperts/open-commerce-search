package de.cxp.ocs.elasticsearch.query;

import java.util.ArrayList;
import java.util.List;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder.FilterFunctionBuilder;

import lombok.Data;

/**
 * A holder for different ways of boosting document matches.
 */
@Data
public class ScoringQueryCollection {

	private final List<FilterFunctionBuilder> mainScoringFunctions = new ArrayList<>();

	private final List<FilterFunctionBuilder> variantScoringFunctions = new ArrayList<>();

	private final List<QueryBuilder> boostingQueries = new ArrayList<>();

	// TODO: negative boosting queries could be added as well and transformed into a boosting query:
	// https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-boosting-query.html

	public void addScoringFunction(FilterFunctionBuilder function, boolean isVariantLevel) {
		if (isVariantLevel) variantScoringFunctions.add(function);
		else mainScoringFunctions.add(function);
	}

	public void addBoostingQuery(QueryBuilder query) {
		boostingQueries.add(query);
	}

}
