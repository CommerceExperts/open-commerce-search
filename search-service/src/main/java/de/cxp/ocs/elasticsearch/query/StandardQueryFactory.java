package de.cxp.ocs.elasticsearch.query;

import static de.cxp.ocs.config.QueryBuildingSetting.analyzer;
import static de.cxp.ocs.config.QueryBuildingSetting.fuzziness;
import static de.cxp.ocs.config.QueryBuildingSetting.isQueryWithShingles;
import static de.cxp.ocs.config.QueryBuildingSetting.minShouldMatch;
import static de.cxp.ocs.config.QueryBuildingSetting.multimatch_type;
import static de.cxp.ocs.config.QueryBuildingSetting.operator;
import static de.cxp.ocs.config.QueryBuildingSetting.phraseSlop;
import static de.cxp.ocs.config.QueryBuildingSetting.quoteAnalyzer;
import static de.cxp.ocs.config.QueryBuildingSetting.tieBreaker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.query.QueryBoosting;
import de.cxp.ocs.elasticsearch.model.term.WeightedTerm;
import de.cxp.ocs.elasticsearch.model.util.EscapeUtil;
import de.cxp.ocs.elasticsearch.model.util.QueryStringUtil;
import de.cxp.ocs.util.ESQueryUtils;
import de.cxp.ocs.util.Util;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StandardQueryFactory {

	@NonNull
	private final Map<QueryBuildingSetting, String>	querySettings;
	private final Map<String, Float>				fieldWeights;
	private final FieldConfigAccess					fieldConfig;

	private final String defaultAnalyzer = "whitespace";

	public SearchQueryWrapper create(ExtendedQuery parsedQuery) {
		String defaultOperator = querySettings.getOrDefault(operator, "OR");
		Fuzziness fuzziness = getFuzziness();
		String queryString = buildQueryString(parsedQuery, !Fuzziness.ZERO.equals(fuzziness));
		
		QueryBuilder esQuery = QueryBuilders.queryStringQuery(queryString)
				.minimumShouldMatch(querySettings.getOrDefault(minShouldMatch, null))
				.analyzer(querySettings.getOrDefault(analyzer, null))
				.quoteAnalyzer(querySettings.getOrDefault(quoteAnalyzer, defaultAnalyzer))
				.fuzziness(fuzziness)
				.defaultOperator(Operator.fromString(defaultOperator))
				.phraseSlop(Util.tryToParseAsNumber(querySettings.getOrDefault(phraseSlop, "0")).orElse(0).intValue())
				.tieBreaker(Util.tryToParseAsNumber(querySettings.getOrDefault(tieBreaker, "0")).orElse(0).floatValue())
				.autoGenerateSynonymsPhraseQuery(false)
				.fields(fieldWeights)
				.queryName(queryString)
				.type(Type.valueOf(querySettings.getOrDefault(multimatch_type, Type.CROSS_FIELDS.name()).toUpperCase()));

		StringBuilder queryDescription = new StringBuilder(queryString);
		if (!parsedQuery.getBoostings().isEmpty()) {
			parsedQuery.getBoostings().forEach(b -> queryDescription.append(" ").append(b));
			esQuery = applyBoostings(esQuery, parsedQuery.getBoostings());
		}

		return new SearchQueryWrapper(esQuery, fuzziness, queryDescription.toString());
	}

	private Fuzziness getFuzziness() {
		String fuzzySetting = querySettings.get(fuzziness);
		Fuzziness fuzziness = Fuzziness.ZERO;
		if ("AUTO".equalsIgnoreCase(fuzzySetting)) {
			fuzziness = Fuzziness.AUTO;
		}
		else if (fuzzySetting != null) {

			Optional<Number> edits = Util.tryToParseAsNumber(fuzzySetting);
			if (edits.isPresent()) {
				fuzziness = Fuzziness.fromEdits(edits.get().intValue());
			}
		}
		return fuzziness;
	}

	private String buildQueryString(ExtendedQuery parsedQuery, boolean isFuzzyEnabled) {
		QueryStringBuilder queryStringBuilder = new QueryStringBuilder().setAddFuzzyMarker(isFuzzyEnabled);
		parsedQuery.getSearchQuery().accept(queryStringBuilder);
		String primaryQuery = queryStringBuilder.getQueryString();

		StringBuilder finalQueryBuilder = new StringBuilder();
		finalQueryBuilder
				.append('(')
				.append(primaryQuery)
				.append(')');

		// search for all terms quoted with higher weight,
		// in order to prefer phrase matches generally
		finalQueryBuilder
				.append(" OR ")
				.append('"')
				.append(getOriginalTermQuery(parsedQuery.getInputTerms()))
				.append('"')
				.append("^1.5");

		// if we have more than one term and the quoteAnalyzer is different than the analyzer,
		// then add a query variant that searches for the single terms quoted on their own.
		if (parsedQuery.getInputTerms().size() > 1 && !querySettings.getOrDefault(analyzer, "").equals(querySettings.get(quoteAnalyzer))) {
			finalQueryBuilder
					.append(" OR ")
					.append('(')
					.append('"')
					.append(getOriginalTermQuery(parsedQuery.getInputTerms(), "\" \""))
					.append('"')
					.append(')')
					.append("^1.1");
		}

		// build shingle variants if enabled
		if (querySettings.getOrDefault(isQueryWithShingles, "false").equalsIgnoreCase("true")) {
			attachQueryTermsAsShingles(parsedQuery.getInputTerms(), finalQueryBuilder);
		}

		if (parsedQuery.getFilters().size() > 0) {
			finalQueryBuilder
					.append(' ')
					.append(QueryStringUtil.buildQueryString(parsedQuery.getFilters(), " "));
		}

		return finalQueryBuilder.toString();
	}

	private String getOriginalTermQuery(List<String> inputTerms) {
		return getOriginalTermQuery(inputTerms, " ");
	}

	private String getOriginalTermQuery(List<String> inputTerms, String delimiter) {
		return inputTerms.stream()
				.map(EscapeUtil::escapeReservedESCharacters)
				.collect(Collectors.joining(delimiter));
	}

	private void attachQueryTermsAsShingles(List<String> inputTerms, StringBuilder queryStringBuilder) {
		for (int i = 0; i < inputTerms.size() - 1; i++) {
			List<String> shingledSearchTerms = new ArrayList<>(inputTerms.size());
			inputTerms.forEach(term -> shingledSearchTerms.add(EscapeUtil.escapeReservedESCharacters(term)));

			String wordA = shingledSearchTerms.remove(i);
			// shingled word has double weight, since it practically matches
			// two input tokens
			WeightedTerm shingleWord = new WeightedTerm(wordA + inputTerms.get(i + 1), 2f);
			shingledSearchTerms.set(i, shingleWord.toQueryString());

			queryStringBuilder.append(" OR ")
					.append('(')
					.append(StringUtils.join(shingledSearchTerms, ' '));

			// the whole variant with shingles has a lower weight
			// than the original terms
			queryStringBuilder.append(")^0.9");
		}
	}

	public QueryBuilder applyBoostings(QueryBuilder queryBuilder, List<QueryBoosting> boostings) {
		float negBoostWeightSum = 0;
		List<QueryBuilder> negativeBoostQueries = new ArrayList<>();

		for (QueryBoosting boosting : boostings) {
			QueryBuilder boostQuery = getBoostingQuery(boosting);
			if (boosting.isUpBoosting()) {
				boostQuery.boost(boosting.getWeight());
				queryBuilder = ESQueryUtils.mapToBoolQueryBuilder(queryBuilder).should(boostQuery).minimumShouldMatch(0);
			}
			else {
				negBoostWeightSum += boosting.getWeight();
				negativeBoostQueries.add(boostQuery);
			}
		}

		if (!negativeBoostQueries.isEmpty()) {
			QueryBuilder negativeBoostQuery;
			if (negativeBoostQueries.size() == 1) {
				negativeBoostQuery = negativeBoostQueries.get(0);
			}
			else {
				negativeBoostQuery = QueryBuilders.boolQuery();
				StringBuilder queryName = new StringBuilder();
				for (QueryBuilder q : negativeBoostQueries) {
					((BoolQueryBuilder) negativeBoostQuery).should(q);
					queryName.append(" ").append(q.queryName());
				}
				negativeBoostQuery.queryName(queryName.toString().trim());
			}
			// in case different negative boost values were specified, we use the average of them, since we cannot
			// apply separate negative boost values
			float negativeBoost = negBoostWeightSum / negativeBoostQueries.size();
			// if DOWN-boosts were specified with bigger values like "10", we simply use the reciprocal value
			if (negativeBoost > 1f) negativeBoost = 1 / negativeBoost;
			queryBuilder = QueryBuilders.boostingQuery(queryBuilder, negativeBoostQuery).negativeBoost(negativeBoost);
		}
		return queryBuilder;
	}

	private QueryBuilder getBoostingQuery(QueryBoosting boosting) {
		QueryBuilder matchBoostQuery;
		Optional<SearchField> searchField = ESQueryUtils.validateSearchField(boosting.getField(), fieldConfig);
		String namePrefix = boosting.isUpBoosting() ? "++" : "--";
		if (searchField.isPresent()) {
			matchBoostQuery = QueryBuilders.matchQuery(searchField.get().getFullName(), boosting.getRawTerm())
					.operator(Operator.AND)
					.analyzer(defaultAnalyzer)
					.queryName(namePrefix + boosting.getField() + ":" + boosting.getRawTerm() + "(" + boosting.getWeight() + ")");
		}
		else {
			matchBoostQuery = QueryBuilders.multiMatchQuery(boosting.getRawTerm()).fields(fieldWeights).analyzer(defaultAnalyzer)
					.operator(Operator.AND)
					.queryName(namePrefix + boosting.getRawTerm());
		}

		return matchBoostQuery;
	}

}
