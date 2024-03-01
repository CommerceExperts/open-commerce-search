package de.cxp.ocs.elasticsearch.query.builder;

import static de.cxp.ocs.config.QueryBuildingSetting.analyzer;
import static de.cxp.ocs.util.ESQueryUtils.validateSearchFields;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.*;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.query.MultiTermQuery;
import de.cxp.ocs.elasticsearch.model.term.AssociatedTerm;
import de.cxp.ocs.elasticsearch.model.term.Occur;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.TextMatchQuery;
import de.cxp.ocs.spi.search.ESQueryFactory;
import de.cxp.ocs.util.ESQueryUtils;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Query factory that analyzes the search keywords and already checks
 * Elasticsearch about which terms hit documents together (including spell
 * correction and term shingles).
 * Based on that analysis a query is built that tries to match most of the
 * terms (magic algorithm ;)).
 * </p>
 * Supported {@link QueryBuildingSetting}s:
 * <ul>
 * <li>'analyzer' that is used to match the configured fields.</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class PredictionQueryFactory implements ESQueryFactory, FallbackConsumer {

	@NonNull
	private final QueryPredictor metaFetcher;

	private final Map<QueryBuildingSetting, String> settings = new HashMap<>();

	private final Map<String, Float> fieldWeights = new HashMap<>();

	private final int desiredResultCount = 12;

	@Setter
	private ESQueryFactory fallbackQueryBuilder;

	private VariantQueryFactory variantQueryFactory;

	@Getter
	@Setter
	private String name;

	@Override
	public void initialize(String name, Map<QueryBuildingSetting, String> settings, Map<String, Float> fieldWeights, FieldConfigAccess fieldConfig) {
		if (name != null) this.name = name;
		this.settings.putAll(settings);
		this.fieldWeights.putAll(validateSearchFields(fieldWeights, fieldConfig, Field::isMasterLevel));
		metaFetcher.setAnalyzer(settings.get(QueryBuildingSetting.analyzer));
		variantQueryFactory = new VariantQueryFactory(validateSearchFields(fieldWeights, fieldConfig, Field::isVariantLevel));
		Optional.ofNullable(settings.get(QueryBuildingSetting.analyzer)).ifPresent(variantQueryFactory::setAnalyzer);
	}

	@Override
	public TextMatchQuery<QueryBuilder> createQuery(ExtendedQuery parsedQuery) {
		final List<PredictedQuery> predictedQueries = predictQueries(parsedQuery);

		if (predictedQueries == null || predictedQueries.isEmpty()) {
			if (fallbackQueryBuilder == null) {
				return null;
			}
			else {
				return fallbackQueryBuilder.createQuery(parsedQuery);
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
						pQuery.termsUnique.put(unknownTerm.getRawTerm(), unknownTerm);

						// ..and increment the matching term count
						matchingTermCount++;
						unknownTermIterator.remove();
					}
					else if (i == 0) {
						unmatchedTerms.put(unknownTerm.getRawTerm(), unknownTerm);
					}
				}
			}
			else if (i == 0) {
				pQuery.unknownTerms.forEach(term -> unmatchedTerms.put(term.getRawTerm(), term));
			}

			String queryLabel = ESQueryUtils.getQueryLabel(pQuery.getTermsUnique().values());
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
			if (matchingTermCount == parsedQuery.getTermCount()
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
			TextMatchQuery<QueryBuilder> boostQuery = fallbackQueryBuilder.createQuery(new ExtendedQuery(new MultiTermQuery(unmatchedTerms.values())));
			mainQuery = QueryBuilders.boolQuery()
					.must(mainQuery)
					.should(boostQuery.getMasterLevelQuery())
					.queryName("boost(" + ESQueryUtils.getQueryLabel(unmatchedTerms.values()) + ")");
		}

		for (QueryStringTerm term : parsedQuery.getFilters()) {
			if (term.getOccur().equals(Occur.MUST_NOT)) {
				mainQuery = ESQueryUtils.mapToBoolQueryBuilder(mainQuery).mustNot(exactMatchQuery(term.getRawTerm()));
			}
		}

		/**
		 * We have copied all searchable variant-level data to master level and
		 * skip searching on variant level in the first step.
		 *
		 * Now prefer variants with more matching terms
		 */
		QueryBuilder variantScoreQuery = variantQueryFactory.createMatchAnyTermQuery(parsedQuery);

		return new TextMatchQuery<>(mainQuery, variantScoreQuery, true, unmatchedTerms.size() == 0);
	}

	private boolean termHasMatches(QueryStringTerm unknownTerm) {
		if (unknownTerm instanceof AssociatedTerm) return ((AssociatedTerm) unknownTerm).getRelatedTerms().size() > 0;
		if (unknownTerm instanceof CountedTerm) return ((CountedTerm) unknownTerm).getTermFrequency() > 0;
		else return false;
	}

	private List<PredictedQuery> predictQueries(final @NonNull ExtendedQuery analyzedQuery) {
		List<PredictedQuery> queryMetaData = null;

		try {
			queryMetaData = metaFetcher.getQueryMetaData(analyzedQuery, fieldWeights);
		}
		catch (final ElasticsearchException | IOException ex) {
			log.error("can't build search query, because meta fetch phase failed", ex);
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

	private QueryBuilder exactMatchQuery(String word) {
		return QueryBuilders.multiMatchQuery(word, fieldWeights.keySet().toArray(new String[0]))
				.analyzer("whitespace")
				.operator(Operator.AND);
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
