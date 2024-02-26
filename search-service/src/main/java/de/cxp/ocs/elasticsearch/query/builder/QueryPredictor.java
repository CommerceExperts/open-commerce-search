package de.cxp.ocs.elasticsearch.query.builder;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.common.util.set.Sets;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.*;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram.Bucket;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.suggest.SuggestBuilder;

import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.SpellCorrector;
import de.cxp.ocs.elasticsearch.model.query.AnalyzedQuery;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.term.AssociatedTerm;
import de.cxp.ocs.elasticsearch.model.term.Occur;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.model.term.WeightedTerm;
import de.cxp.ocs.elasticsearch.model.visitor.QueryTermVisitor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * this code is hard to test due to it's dependency to ES.
 * TODO: inject possible aggregation results (buckets)
 */
@RequiredArgsConstructor
class QueryPredictor {

	private final RestHighLevelClient	restClient;
	private final String				indices;

	@Setter
	private String analyzer;

	protected List<PredictedQuery> getQueryMetaData(final @NonNull ExtendedQuery parsedQuery, final Map<String, Float> fieldWeights)
			throws IOException {
		// put "must-not" terms into separate list
		List<QueryStringTerm> searchWordsCleaned = new ArrayList<>(parsedQuery.getInputTerms().size());
		List<QueryStringTerm> excludeFilters = new ArrayList<>(parsedQuery.getFilters().size());
		parsedQuery.accept(new QueryTermVisitor() {

			@Override
			public void visitTerm(QueryStringTerm term) {
				if (term.getOccur().equals(Occur.MUST_NOT)) {
					excludeFilters.add(term);
				}
				else {
					searchWordsCleaned.add(term);
				}
			}

			@Override
			public void visitSubQuery(AnalyzedQuery query) {
				// only collect terms from the root query or first sub query
				if (searchWordsCleaned.isEmpty() && excludeFilters.isEmpty()) {
					query.accept(this);
				}
			}
		});

		// create ordered shingles from the original words
		List<String> inputTerms = parsedQuery.getInputTerms();
		final Map<String, Set<String>> shingles = createOrderedShingles(inputTerms);
		final Map<String, Set<String>> shingleSources = invertedIndex(shingles);

		// ..and add them to the list of searched terms
		final Set<QueryStringTerm> actualSearchTerms = new HashSet<>(searchWordsCleaned);
		shingles.keySet().forEach(shingleWord -> actualSearchTerms.add(new WeightedTerm(shingleWord)));

		final SpellCorrector corrector = new SpellCorrector(fieldWeights.keySet());
		final Map<Float, CountedTerm> predictionWords = new LinkedHashMap<>();
		final SearchResponse searchResponse = runTermAnalysis(
				fieldWeights.keySet(),
				actualSearchTerms,
				excludeFilters,
				predictionWords,
				corrector);

		final Map<String, AssociatedTerm> correctedWords = corrector.extractRelatedWords(searchResponse.getSuggest());
		final Map<String, PredictedQuery> predictedQueries = new HashMap<>();
		final Set<String> redundantQueries = new HashSet<>();
		boolean hasFoundQueryWithAllTermsMatching = false;
		for (final Bucket scoreBucket : ((Histogram) searchResponse.getAggregations().get("_score_histogram"))
				.getBuckets()) {
			final PredictedQuery predictedQuery = new PredictedQuery();
			final LinkedHashMap<String, QueryStringTerm> matchingTerms = getMatchingTerms(predictionWords, scoreBucket);
			if (matchingTerms.size() == 0) continue;
			if (matchingTerms.size() == 1) {
				String matchedTerm = matchingTerms.keySet().iterator().next();
				predictionWords.values().stream()
						.filter(term -> term.getRawTerm().equals(matchedTerm))
						.findFirst()
						.ifPresent(term -> term.setTermFrequency((int) scoreBucket.getDocCount()));
			}

			predictedQuery.matchCount = scoreBucket.getDocCount();
			predictedQuery.termsUnique.putAll(matchingTerms);
			applyTermMatches(inputTerms, shingleSources, predictedQuery, correctedWords);
			hasFoundQueryWithAllTermsMatching ^= predictedQuery.isContainsAllTerms();

			// if one of the searched terms matches documents but also has
			// "spell corrections",
			// remove all spell corrections that match less words
			// XXX this might cause problems, if a precise term is similar to a
			// broad term
			if (predictedQuery.originalTermCount == 1) {
				String matchedTerm = predictedQuery.getTermsUnique().keySet().iterator().next();
				correctedWords.computeIfPresent(matchedTerm, (k, v) -> {
					if (v instanceof AssociatedTerm) {
						Iterator<QueryStringTerm> relatedWordIterator = v.getRelatedTerms().values().iterator();
						while (relatedWordIterator.hasNext()) {
							CountedTerm relWord = (CountedTerm) relatedWordIterator.next();
							if (relWord.getTermFrequency() < predictedQuery.matchCount) {
								relatedWordIterator.remove();
							}
						}
					}
					return v;
				});
			}

			/* * *
			 * if we have matches that contain a shingle AND the shingles'
			 * source like "blaues jersey kleid jerseykleid", create an
			 * additional query without "jersey kleid" (both are source terms of
			 * the containing shingle "jerseykleid")
			 * => "blaues jerseykleid"
			 *
			 * or from "blaues kleid jerseykleid", create a query without
			 * "kleid" (source term of the containing shingle "jerseykleid")
			 * => "blaues jerseykleid"
			 *
			 * The original queries (that contain redundant words) will be
			 * deleted afterwards (see redundantQueries)
			 *
			 * because we can generate duplicates with that approach, check if
			 * we already have "blaues jerseykleid".
			 * * */
			final Optional<Set<String>> shingledTermsUnique = getWithoutShingleSources(predictedQuery.termsUnique
					.keySet(),
					shingles);
			if (shingledTermsUnique.isPresent()) {
				redundantQueries.add(getQueryKey(predictedQuery));

				matchingTerms.keySet().retainAll(shingledTermsUnique.get());
				final PredictedQuery reducedQuery = new PredictedQuery();
				reducedQuery.termsUnique.putAll(matchingTerms);
				// reducedQuery.matchCount = predictedQuery.matchCount;
				reducedQuery.containsAllTerms = predictedQuery.containsAllTerms;
				reducedQuery.originalTermCount = predictedQuery.originalTermCount;
				reducedQuery.unknownTerms = predictedQuery.unknownTerms;

				putOrMerge(predictedQueries, reducedQuery);
			}

			fixMatchCount(predictedQueries, predictedQuery);
			putOrMerge(predictedQueries, predictedQuery);
		}

		if (!hasFoundQueryWithAllTermsMatching) {
			for (final AssociatedTerm correctedWord : correctedWords.values()) {
				if (predictedQueries.containsKey(correctedWord.getMainTerm().getRawTerm())) continue;

				final PredictedQuery predictedQuery = new PredictedQuery();
				predictedQuery.termsUnique.put(correctedWord.getMainTerm().getRawTerm(), correctedWord);
				// sum the term frequencies of all corrected words
				predictedQuery.matchCount = correctedWord.getRelatedTerms().values().stream()
						.mapToLong(ww -> ((CountedTerm) ww).getTermFrequency()).sum();

				applyTermMatches(inputTerms, shingleSources, predictedQuery, correctedWords);
				predictedQueries.put(getQueryKey(predictedQuery), predictedQuery);
			}
		}

		redundantQueries.forEach(rq -> predictedQueries.remove(rq));

		return new ArrayList<>(predictedQueries.values());
	}

	private void putOrMerge(final Map<String, PredictedQuery> allQueries, final PredictedQuery addQuery) {
		PredictedQuery previousQuery = allQueries.put(getQueryKey(addQuery), addQuery);
		if (previousQuery != null) {
			addQuery.matchCount += previousQuery.matchCount;
			StringBuilder queryString = new StringBuilder(previousQuery.getQueryString());
			if (queryString.charAt(0) != '(') {
				queryString.insert(0, '(').append(')');
			}
			queryString.append(" OR ").append('(').append(addQuery.getQueryString()).append(')');
			addQuery.setQueryString(queryString.toString());
		}
	}

	private String getQueryKey(PredictedQuery predictedQuery) {
		return StringUtils.join(predictedQuery.getTermsUnique().keySet(), "");
	}

	/**
	 *
	 * @param fieldWeights
	 *        fields to search
	 * @param terms
	 * @param excludeFilters
	 * @param predictionWords
	 *        empty map that will be filled with the exponential boost values of
	 *        each term
	 * @param corrector
	 * @return
	 * @throws IOException
	 */
	@SuppressWarnings("deprecation")
	private SearchResponse runTermAnalysis(final Collection<String> searchFields, final Set<QueryStringTerm> terms,
			List<QueryStringTerm> excludeFilters, final Map<Float, CountedTerm> predictionWords, final SpellCorrector corrector) throws IOException {
		final SuggestBuilder spellCheckQuery = corrector.buildSpellCorrectionQuery(
				terms.stream().map(qst -> qst.getRawTerm()).collect(Collectors.joining(" ")));

		final BoolQueryBuilder metaFetchQuery = QueryBuilders.boolQuery();
		// FIXME: i guess this is wrong: adding exclude filters as boolean-filter clause
		excludeFilters.forEach(term -> metaFetchQuery.filter(buildTermQuery(term, searchFields)));

		// build boolean-should query with exponential boost values
		float queryWeight = (float) Math.pow(2, terms.size() - 1);
		for (final QueryStringTerm term : terms) {
			metaFetchQuery.should(QueryBuilders
					.constantScoreQuery(buildTermQuery(term, searchFields))
					.boost(queryWeight));
			predictionWords.put(queryWeight, new CountedTerm(term));
			queryWeight = queryWeight / 2;
		}

		// add score histogram that is used later to retrieve the match counts
		// of the single terms and their intersections
		final AggregationBuilder scriptAgg = AggregationBuilders
				.histogram("_score_histogram")
				.interval(1)
				.minDocCount(1)
				.script(new Script("_score"));

		final SearchResponse searchResponse = restClient
				.search(new SearchRequest(indices)
						.source(SearchSourceBuilder.searchSource()
								.suggest(spellCheckQuery)
								.query(metaFetchQuery)
								.aggregation(scriptAgg)
								.size(0)
								.timeout(TimeValue.timeValueMillis(20))),
						RequestOptions.DEFAULT);
		return searchResponse;
	}

	protected QueryBuilder buildTermQuery(final QueryStringTerm term, final Collection<String> searchFields) {
		final QueryStringQueryBuilder wordQuery = QueryBuilders
				.queryStringQuery(term.toQueryString())
				.fuzziness(Fuzziness.ZERO)
				.analyzer(analyzer)
				// in case the analyzer splits the term in a different way then we have,
				// we want to make sure all terms are matched and not just one of them
				.defaultOperator(Operator.AND);

		if (searchFields == null) {
			wordQuery.field(FieldConstants.SEARCH_DATA + ".*");
		}
		else {
			for (String fieldName : searchFields) {
				if (!fieldName.startsWith(FieldConstants.SEARCH_DATA)) {
					fieldName = FieldConstants.SEARCH_DATA + "." + fieldName;
				}
				wordQuery.field(fieldName);
			}
		}

		return wordQuery;
	}

	/**
	 * From each score aggregation bucket, we can reconstruct the matching
	 * terms.
	 *
	 * @param weightsPerTerm
	 * @param scoreBucket
	 * @return
	 */
	private LinkedHashMap<String, QueryStringTerm> getMatchingTerms(final Map<Float, CountedTerm> weightsPerTerm,
			final Bucket scoreBucket) {
		final LinkedHashMap<String, QueryStringTerm> termsOrdered = new LinkedHashMap<>();
		float additiveWeight = Float.parseFloat(scoreBucket.getKeyAsString());
		for (final Entry<Float, CountedTerm> weightPerTerm : weightsPerTerm.entrySet()) {
			if (weightPerTerm.getKey() <= additiveWeight) {
				additiveWeight -= weightPerTerm.getKey();
				termsOrdered.put(weightPerTerm.getValue().getRawTerm(), weightPerTerm.getValue());
				if (additiveWeight == 0) {
					break;
				}
			}
		}
		return termsOrdered;
	}

	private void applyTermMatches(final List<String> inputTerms,
			final Map<String, Set<String>> shingleSources,
			final PredictedQuery predictedQuery, final Map<String, AssociatedTerm> correctedWords) {
		int originalTermCount = 0;
		for (final String term : inputTerms) {
			if (predictedQuery.termsUnique.containsKey(term)) {
				originalTermCount++;
				continue;
			}

			final Set<String> shinglesWithTerm = shingleSources.get(term);
			if (shinglesWithTerm != null && containsAny(predictedQuery.termsUnique.keySet(), shinglesWithTerm)) {
				originalTermCount++;
			}
			else {
				final AssociatedTerm correctedWord = correctedWords.get(term);
				predictedQuery.unknownTerms.add(correctedWord != null ? correctedWord : new WeightedTerm(term));
			}
		}
		predictedQuery.containsAllTerms = originalTermCount == inputTerms.size();
		predictedQuery.originalTermCount = originalTermCount;
	}

	private Map<String, Set<String>> createOrderedShingles(final List<String> searchWords) {
		final Map<String, Set<String>> shingles = new HashMap<>();

		for (int i = 0; i < searchWords.size() - 1; i++) {
			shingles.put(searchWords.get(i) + searchWords.get(i + 1),
					Sets.newHashSet(searchWords.get(i), searchWords.get(i + 1)));
		}
		return shingles;
	}

	private Optional<Set<String>> getWithoutShingleSources(final Set<String> termsUnique,
			final Map<String, Set<String>> shingles) {
		final Set<String> uniqueTokens = new HashSet<>(termsUnique);
		for (final Entry<String, Set<String>> shingle : shingles.entrySet()) {
			if (termsUnique.contains(shingle.getKey())) {
				uniqueTokens.removeAll(shingle.getValue());
			}
		}
		if (uniqueTokens.size() > 0 && uniqueTokens.size() < termsUnique.size()) {
			return Optional.of(uniqueTokens);
		}
		else {
			return Optional.empty();
		}
	}

	private <K, V> Map<V, Set<K>> invertedIndex(final Map<K, Set<V>> values) {
		final Map<V, Set<K>> invertedIndex = new HashMap<>();
		for (final Entry<K, Set<V>> entry : values.entrySet()) {
			entry.getValue().forEach(v -> invertedIndex
					.computeIfAbsent(v, x -> new HashSet<>())
					.add(entry.getKey()));
		}
		return invertedIndex;
	}

	/**
	 * return true if haystack contains any of the needles.
	 * if haystack is empty, it returns false!
	 * if needles is empty, it returns false!
	 *
	 * @param haystack
	 * @param needles
	 * @return
	 */
	private boolean containsAny(final Set<String> haystack, final Collection<String> needles) {
		if (haystack.size() == 0) {
			return false;
		}
		if (needles.size() == 0) {
			return false;
		}
		if (haystack.size() == 1) {
			return needles.contains(haystack.iterator().next());
		}

		for (final String value : needles) {
			if (haystack.contains(value)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * <p>
	 * fixes the match count of a query
	 * </p>
	 * <p>
	 * since the terms (+ shingles) could generate matches, that contain
	 * "intersection matches", we don't get complete numbers from the
	 * aggregations. instead we could get a result like this:
	 * </p>
	 *
	 * <pre>
	 * 'jersey kleid jerseykleid' match count: 6
	 * 'kleid jerseykleid' match count: 258
	 * 'jerseykleid' match count: 1
	 * </pre>
	 * <p>
	 * As you can see, "jerseykleid" only has matchcount 1 although in
	 * combination with other words (AND searches) we have more matches!
	 * =>
	 * What we can say for sure here, is that we can add the matchcounts of
	 * the "longer phrases" to the "shorter phrases" that contain all terms
	 * </p>
	 *
	 * <pre>
	 * 'jerseykleid' match count: 1+258+6
	 * 'kleid jerseykleid' match count: 258+6
	 * </pre>
	 **/
	private void fixMatchCount(final Map<String, PredictedQuery> otherQueries, final PredictedQuery currentQuery) {
		for (final PredictedQuery otherQuery : otherQueries.values()) {
			if (otherQuery.termsUnique.size() > currentQuery.termsUnique.size()
					&& otherQuery.termsUnique.keySet().containsAll(currentQuery.termsUnique.keySet())) {
				currentQuery.matchCount += otherQuery.matchCount;
			}
			else if (otherQuery.termsUnique.size() < currentQuery.termsUnique.size()
					&& currentQuery.termsUnique.keySet().containsAll(otherQuery.termsUnique.keySet())) {
				otherQuery.matchCount += currentQuery.matchCount;
			}
		}
	}

}
