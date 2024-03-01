package de.cxp.ocs.elasticsearch.query.builder;

import static de.cxp.ocs.util.ESQueryUtils.validateSearchFields;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.query.MultiTermQuery;
import de.cxp.ocs.elasticsearch.model.term.Occur;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.StandardQueryFactory;
import de.cxp.ocs.elasticsearch.query.TextMatchQuery;
import de.cxp.ocs.spi.search.ESQueryFactory;
import de.cxp.ocs.util.ESQueryUtils;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Query factory uses the QueryPredictor to determine, which terms work together.
 * Based on that a query is built that combines the results for all valid term-combinations including some boosting for
 * the unknown terms.
 * 
 * @author Rudolf Batt
 */
@Slf4j
@RequiredArgsConstructor
public class RelaxedQueryFactory implements ESQueryFactory {

	@NonNull
	private final QueryPredictor metaFetcher;

	private final Map<QueryBuildingSetting, String> settings = new HashMap<>();

	private final Map<String, Float> fieldWeights = new HashMap<>();

	@Setter
	private ESQueryFactory fallbackQueryBuilder;

	private StandardQueryFactory mainQueryFactory;

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
		// set recommended default settings
		settings.putIfAbsent(QueryBuildingSetting.operator, "and");
		settings.putIfAbsent(QueryBuildingSetting.phraseSlop, "5");
		mainQueryFactory = new StandardQueryFactory(settings, this.fieldWeights);
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

		// all terms of our first/preferred query that do not have a
		// spell-correction alternative, will be collected for later boosting
		// if all terms are matched (so this map is empty) this query builder
		// rejects other queries afterwards
		final Map<String, QueryStringTerm> unmatchedTerms = new HashMap<>();
		Set<String> createdQueries = new LinkedHashSet<>();

		int i = 0;
		for (final PredictedQuery pQuery : predictedQueries) {
			int matchingTermCount = pQuery.getOriginalTermCount();

			// remember how many terms at least must match..
			if (minimumTermsMustMatch < 1) {
				minimumTermsMustMatch = matchingTermCount;
			}
			// ..if we already have queries that match a certain amount of
			// terms, don't use queries that would match less terms
			else if (matchingTermCount < minimumTermsMustMatch) {
				break;
			}


			String queryLabel = ESQueryUtils.getQueryLabel(pQuery.getTermsUnique().values());
			// the label represents the complete query! if the label already
			// exists, the query also already exists
			if (!createdQueries.add(queryLabel)) continue;

			// fetch unmatched terms from first query
			if (i == 0) {
				pQuery.unknownTerms.forEach(term -> unmatchedTerms.put(term.getRawTerm(), term));
			}
			// for all others, check if they match one of those unmatched ones
			else if (unmatchedTerms.size() > 0) {
				pQuery.getTermsUnique().keySet().forEach(unmatchedTerms::remove);
			}


			QueryBuilder matchQuery = mainQueryFactory.create(new ExtendedQuery(new MultiTermQuery(pQuery.getTermsUnique().values())));
			matchQuery.boost(pQuery.originalTermCount);
			matchQuery.queryName(queryLabel);
			mainQuery = mergeToBoolShouldQuery(mainQuery, matchQuery);

			i++;
		}

		// in case we have some terms that are not matched by any query, use
		// them with the fallback query builder to boost matching records.
		if (unmatchedTerms.size() > 0 && fallbackQueryBuilder != null) {
			QueryBuilder boostQuery = fallbackQueryBuilder.createQuery(new ExtendedQuery(new MultiTermQuery(unmatchedTerms.values()))).getMasterLevelQuery();
			mainQuery = QueryBuilders.boolQuery().must(mainQuery).should(boostQuery);
			// add query-description for label
			createdQueries.add("boost(" + ESQueryUtils.getQueryLabel(unmatchedTerms.values()) + ")");
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

		return new TextMatchQuery<>(mainQuery, variantScoreQuery, true, unmatchedTerms.size() == 0, StringUtils.join(createdQueries, " + "));
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
