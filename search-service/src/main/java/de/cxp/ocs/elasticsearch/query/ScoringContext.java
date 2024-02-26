package de.cxp.ocs.elasticsearch.query;

import java.util.ArrayList;
import java.util.List;

import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder.FilterFunctionBuilder;

import de.cxp.ocs.util.ESQueryUtils;
import lombok.Data;

/**
 * A holder for different ways of boosting document matches.
 */
@Data
public class ScoringContext {

	private CombineFunction	boostMode	= CombineFunction.MULTIPLY;
	private ScoreMode		scoreMode	= ScoreMode.MULTIPLY;

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

	public QueryBuilder wrapMasterLevelQuery(QueryBuilder query) {
		if (!boostingQueries.isEmpty()) {
			BoolQueryBuilder boolQueryBuilder;
			// if the query is a boolean query and from the should clauses at least one (or more) clauses must match
			// (having a minShouldMatch defined), then we have to wrap that boolean query into the must clause of
			// another boolean query
			if (query instanceof BoolQueryBuilder && !((BoolQueryBuilder) query).should().isEmpty() && ((BoolQueryBuilder) query).minimumShouldMatch() != null) {
				boolQueryBuilder = QueryBuilders.boolQuery().must(query);
			}
			else {
				boolQueryBuilder = ESQueryUtils.mapToBoolQueryBuilder(query);
			}
			boostingQueries.forEach(boolQueryBuilder::should);
			query = boolQueryBuilder;
		}

		if (!mainScoringFunctions.isEmpty()) {
			query = QueryBuilders.functionScoreQuery(query, mainScoringFunctions.toArray(new FilterFunctionBuilder[0]))
					.boostMode(boostMode)
					.scoreMode(scoreMode);
		}
		return query;
	}

	public QueryBuilder wrapVariantLevelQuery(QueryBuilder variantsMatchQuery) {
		if (!variantScoringFunctions.isEmpty()) {
			if (variantsMatchQuery == null) variantsMatchQuery = QueryBuilders.matchAllQuery();
			variantsMatchQuery = QueryBuilders.functionScoreQuery(variantsMatchQuery, variantScoringFunctions.toArray(new FilterFunctionBuilder[0]))
					.boostMode(boostMode)
					.scoreMode(scoreMode);
		}
		return variantsMatchQuery;
	}

}
