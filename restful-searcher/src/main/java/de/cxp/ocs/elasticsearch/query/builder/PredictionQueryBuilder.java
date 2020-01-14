package de.cxp.ocs.elasticsearch.query.builder;

import static de.cxp.ocs.config.QueryBuildingSetting.analyzer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;

import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WeightedWord;
import de.cxp.ocs.elasticsearch.query.model.WordAssociation;
import de.cxp.ocs.util.QueryUtils;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
public class PredictionQueryBuilder implements ESQueryBuilder {

	@NonNull
	private final QueryPredictor metaFetcher;

	private final Map<QueryBuildingSetting, String> settings;

	private final Map<String, Float> fieldWeights;

	private final int desiredResultCount = 12;

	@Setter
	private ESQueryBuilder fallbackQueryBuilder;

	@Getter
	@Setter
	private String name;

	@Override
	public MasterVariantQuery buildQuery(final List<QueryStringTerm> searchWords) {
		final List<PredictedQuery> predictedQueries = predictQueries(searchWords);

		if (predictedQueries == null || predictedQueries.isEmpty()) {
			if (fallbackQueryBuilder == null) {
				return null;
			}
			else {
				return fallbackQueryBuilder.buildQuery(searchWords);
			}
		}

		int minimumTermsMustMatch = -1;
		QueryBuilder mainQuery = null;

		// all terms of our first/prefered query that do not have a
		// spell-correction alternative, will be collected for later boosting
		// if all terms are matched (so this map is empty) this query builder
		// rejects other queries afterwards
		final Map<String, QueryStringTerm> unmatchedTerms = new HashMap<>();
		Set<String> createdQueries = new HashSet<>();
		long expectedMatchCount = 0;
		int i = 0;
		for (final PredictedQuery pQuery : predictedQueries) {
			int matchingTermCount = pQuery.getOriginalTermCount();
			List<String> queryStrings = new ArrayList<>();
			queryStrings.add("(" + pQuery.getQueryString() + ")^" + pQuery.getOriginalTermCount());

			// if any word of the predicted query has spell corrections, the
			// prediction is not precise enough. In that case we use all terms
			// with matches as required terms
			if (pQuery.getCorrectedTermCount() > 0) {
				final Iterator<QueryStringTerm> unknownTermIterator = pQuery.getUnknownTerms().iterator();
				while (unknownTermIterator.hasNext()) {
					final QueryStringTerm unknownTerm = unknownTermIterator.next();
					if (termHasMatches(unknownTerm)) {
						queryStrings.add(unknownTerm.toQueryString());
						pQuery.termsUnique.put(unknownTerm.getWord(), unknownTerm);

						// ..and increment the matching term count
						matchingTermCount++;
						unknownTermIterator.remove();
					}
					else if (i == 0) {
						unmatchedTerms.put(unknownTerm.getWord(), unknownTerm);
					}
				}
			}
			else if (i == 0) {
				pQuery.unknownTerms.forEach(term -> unmatchedTerms.put(term.getWord(), term));
			}

			String queryLabel = QueryUtils.getQueryLabel(pQuery.getTermsUnique().values());
			// the label represents the complete query! if the label already
			// exists, the query also already exists
			if (mainQuery != null && createdQueries.add(queryLabel)) continue;

			// remember how many terms at least must match..
			if (minimumTermsMustMatch < 1) {
				minimumTermsMustMatch = matchingTermCount;
			}
			// ..if we already have queries that match a certain amount of
			// terms, don't use queries that would match less terms
			else if (matchingTermCount < minimumTermsMustMatch) {
				break;
			}

			// check how many unmatched queries are matched
			int consideredUnmatchedQueries = 0;
			if (unmatchedTerms.size() > 0) {
				consideredUnmatchedQueries = unmatchedTerms.size();
				pQuery.getTermsUnique().keySet().forEach(unmatchedTerms::remove);
				consideredUnmatchedQueries -= unmatchedTerms.size();
			}

			// Use the query if it requires all terms to match
			if (matchingTermCount == searchWords.size()
					// ..OR..
					// use the query if it contains any of the unmatched terms
					// => TODO: put and use QueryMetaFetcher.containsAny method
					// into utils
					|| consideredUnmatchedQueries > 0
					// ..OR..
					// use the query if the desired result count was not
					// reached yet
					|| expectedMatchCount < desiredResultCount) {
				expectedMatchCount += pQuery.getMatchCount();

				final QueryBuilder allTermsMustMatch = buildAllQueriesMustMatchQuery(queryStrings);
				allTermsMustMatch.boost(pQuery.originalTermCount);
				allTermsMustMatch.queryName(queryLabel);
				mainQuery = mergeToBoolShouldQuery(mainQuery, allTermsMustMatch);
			}
			else {
				break;
			}
			i++;
		}

		// in case we have some terms that are not matched by any query, use
		// them with the fallback query builder to boost matching records.
		if (unmatchedTerms.size() > 0 && fallbackQueryBuilder != null) {
			MasterVariantQuery boostQuery = fallbackQueryBuilder.buildQuery(new ArrayList<>(unmatchedTerms.values()));
			mainQuery = QueryBuilders.boolQuery()
					.must(mainQuery)
					.should(boostQuery.getMasterLevelQuery())
					.queryName("boost(" + QueryUtils.getQueryLabel(unmatchedTerms.values()) + ")");
		}

		/**
		 * We have copied all searchable variant-level data to master level and
		 * skip searching on variant level in the first step.
		 *
		 * Now prefer variants with more matching terms
		 */
		final QueryStringQueryBuilder variantScoreQuery = QueryBuilders.queryStringQuery(
				QueryUtils.buildQueryString(searchWords, " "))
				.defaultField(FieldConstants.VARIANTS + "." + FieldConstants.SEARCH_DATA + ".*.standard")
				.queryName("variants.boost(" + QueryUtils.getQueryLabel(searchWords) + ")");
		variantScoreQuery.type(Type.MOST_FIELDS);

		return new MasterVariantQuery(mainQuery, variantScoreQuery, true, unmatchedTerms.size() == 0);
	}

	private boolean termHasMatches(QueryStringTerm unknownTerm) {
		if (unknownTerm instanceof WordAssociation) return ((WordAssociation) unknownTerm).getRelatedWords().size() > 0;
		if (unknownTerm instanceof WeightedWord) return ((WeightedWord) unknownTerm).getTermFrequency() > 0;
		else return false;
	}

	private List<PredictedQuery> predictQueries(final List<QueryStringTerm> searchWords) {
		List<PredictedQuery> queryMetaData = null;

		try {
			queryMetaData = metaFetcher.getQueryMetaData(searchWords, fieldWeights);
		}
		catch (final IOException ioe) {
			throw new RuntimeException("can't build search query, because meta fetch phase failed", ioe);
		}

		if (queryMetaData == null || queryMetaData.isEmpty()) {
			return null;
		}

		// sort the desired term-combinations to the top:
		Collections.sort(queryMetaData, new Comparator<PredictedQuery>() {

			@Override
			public int compare(final PredictedQuery o1, final PredictedQuery o2) {
				// prefer queries where all terms match
				int compare = Boolean.compare(o2.isContainsAllTerms(), o1.isContainsAllTerms());

				// ..otherwise prefer more terms matching => o2 vs o1
				if (compare == 0) {
					compare = Integer.compare(o2.getOriginalTermCount(), o1.getOriginalTermCount());
				}

				// ..otherwise prefer queries with more corrected terms
				if (compare == 0) {
					compare = Integer.compare(o2.getCorrectedTermCount(), o1.getCorrectedTermCount());
				}

				// ..otherwise prefer lower match counts (mostly more precise)
				// => o1 vs o2
				if (compare == 0) {
					compare = Long.compare(o1.getMatchCount(), o2.getMatchCount());
				}

				return compare;
			}

		});
		return queryMetaData;
	}

	private QueryBuilder buildAllQueriesMustMatchQuery(final List<String> queryStrings) {
		String queryString;
		if (queryStrings.size() == 1) {
			queryString = queryStrings.get(0);
		}
		else {
			queryString = "(" + StringUtils.join(queryStrings, ") AND (") + ")";
		}

		return buildQueryStringQuery(queryString);
	}

	private QueryBuilder buildQueryStringQuery(String queryString) {
		final QueryStringQueryBuilder multiMatchQuery = QueryBuilders
				.queryStringQuery(queryString)
				.analyzer(settings.getOrDefault(analyzer, null))
				.defaultOperator(Operator.AND)
				.fuzziness(Fuzziness.ZERO);
		multiMatchQuery.type(Type.CROSS_FIELDS);

		for (final Entry<String, Float> fieldWeight : fieldWeights.entrySet()) {
			String fieldName = fieldWeight.getKey();
			if (!fieldName.startsWith(FieldConstants.SEARCH_DATA)) {
				fieldName = FieldConstants.SEARCH_DATA + "." + fieldName;
			}
			multiMatchQuery.field(fieldName, fieldWeight.getValue());
		}

		return multiMatchQuery;
	}

	/**
	 * At a boolean query with only should clauses, at least one MUST match. Use
	 * that to combine several queries.
	 * 
	 * @param queryBuilder
	 * @param allTermsMustMatch
	 * @return
	 */
	private QueryBuilder mergeToBoolShouldQuery(QueryBuilder queryBuilder, final QueryBuilder allTermsMustMatch) {
		if (queryBuilder != null) {
			if (!(queryBuilder instanceof BoolQueryBuilder)) {
				queryBuilder = QueryBuilders.boolQuery()
						.minimumShouldMatch(1)
						.should(queryBuilder);
			}
			((BoolQueryBuilder) queryBuilder).should(allTermsMustMatch);
		}
		else {
			queryBuilder = allTermsMustMatch;
		}
		return queryBuilder;
	}

	@Override
	public boolean allowParallelSpellcheckExecution() {
		// in the prefetch phase we already do a spellcheck
		return false;
	}

}
