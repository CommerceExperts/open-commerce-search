package de.cxp.ocs.elasticsearch.query.builder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.common.util.set.Sets;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram.Bucket;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.suggest.SuggestBuilder;

import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.SpellCorrector;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WeightedWord;
import de.cxp.ocs.elasticsearch.query.model.WordAssociation;
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

	protected List<PredictedQuery> getQueryMetaData(final List<QueryStringTerm> searchTerms,
			final Map<String, Float> fieldWeights)
			throws IOException {
		// create ordered shingles from the original words
		final Map<String, Set<String>> shingles = createOrderedShingles(searchTerms);
		final Map<String, Set<String>> shingleSources = invertedIndex(shingles);

		// ..and add them to the list of searched terms
		final Set<QueryStringTerm> actualSearchTerms = new HashSet<>(searchTerms);
		shingles.keySet().forEach(shingleWord -> actualSearchTerms.add(new WeightedWord(shingleWord)));

		final SpellCorrector corrector = getSpellCorrector(fieldWeights.keySet());
		final Map<Float, WeightedWord> predictionWords = new LinkedHashMap<>();
		final SearchResponse searchResponse = runTermAnalysis(
				fieldWeights.keySet(),
				actualSearchTerms,
				predictionWords,
				corrector);

		final Map<String, WordAssociation> correctedWords = corrector.extractRelatedWords(actualSearchTerms,
				searchResponse
						.getSuggest());

		final Map<String, PredictedQuery> predictedQueries = new HashMap<>();
		final Set<String> redundantQueries = new HashSet<>();
		boolean hasFoundQueryWithAllTermsMatching = false;
		for (final Bucket scoreBucket : ((Histogram) searchResponse.getAggregations().get("_score_histogram"))
				.getBuckets()) {
			final PredictedQuery predictedQuery = new PredictedQuery();
			final LinkedHashMap<String, QueryStringTerm> matchingTerms = getMatchingTerms(predictionWords, scoreBucket);
			if (matchingTerms.size() == 1) {
				String matchedTerm = matchingTerms.keySet().iterator().next();
				predictionWords.values().stream()
						.filter(term -> term.getWord().equals(matchedTerm))
						.findFirst()
						.ifPresent(term -> term.setTermFrequency((int) scoreBucket.getDocCount()));
			}

			predictedQuery.matchCount = scoreBucket.getDocCount();
			predictedQuery.termsUnique.putAll(matchingTerms);
			applyTermMatches(searchTerms, shingleSources, predictedQuery, correctedWords);
			hasFoundQueryWithAllTermsMatching ^= predictedQuery.isContainsAllTerms();

			// if one of the searched terms matches documents but also has
			// "spell corrections",
			// remove all spell corrections that match less words
			// XXX this might cause problems, if a precise term is similar to a
			// broad term
			if (predictedQuery.originalTermCount == 1) {
				String matchedTerm = predictedQuery.getTermsUnique().keySet().iterator().next();
				correctedWords.computeIfPresent(matchedTerm, (k, v) -> {
					if (v instanceof WordAssociation) {
						Iterator<WeightedWord> relatedWordIterator = v.getRelatedWords().values().iterator();
						while (relatedWordIterator.hasNext()) {
							WeightedWord relWord = relatedWordIterator.next();
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
			for (final WordAssociation correctedWord : correctedWords.values()) {
				if (predictedQueries.containsKey(correctedWord.getOriginalWord())) continue;

				final PredictedQuery predictedQuery = new PredictedQuery();
				predictedQuery.termsUnique.put(correctedWord.getOriginalWord(), correctedWord);
				// sum the term frequencies of all corrected words
				predictedQuery.matchCount = correctedWord.getRelatedWords().values().stream()
						.mapToLong(ww -> ww.getTermFrequency()).sum();

				applyTermMatches(searchTerms, shingleSources, predictedQuery, correctedWords);
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
	 * @param predictionWords
	 *        empty map that will be filled with the exponential boost values of
	 *        each term
	 * @param corrector
	 * @return
	 * @throws IOException
	 */
	private SearchResponse runTermAnalysis(final Collection<String> searchFields, final Set<QueryStringTerm> terms,
			final Map<Float, WeightedWord> predictionWords, final SpellCorrector corrector) throws IOException {
		final SuggestBuilder spellCheckQuery = corrector.buildSpellCorrectionQuery(
				terms.stream().map(qst -> qst.getWord()).collect(Collectors.joining(" ")));

		// build boolean-should query with exponential boost values
		final BoolQueryBuilder metaFetchQuery = QueryBuilders.boolQuery();
		float queryWeight = (float) Math.pow(2, terms.size() - 1);
		for (final QueryStringTerm term : terms) {
			metaFetchQuery.should(QueryBuilders
					.constantScoreQuery(buildTermQuery(term, searchFields))
					.boost(queryWeight));
			predictionWords.put(queryWeight, term instanceof WeightedWord ? (WeightedWord) term
					: new WeightedWord(term.getWord()));
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

	private SpellCorrector getSpellCorrector(final Collection<String> searchFields) {
		final Set<String> spellCheckFields = new HashSet<>();
		for (String searchField : searchFields) {
			if (searchField == null || searchField.contains("*")) {
				continue;
			}
			if (searchField.contains(".")) {
				searchField = searchField.substring(0, searchField.indexOf('.'));
			}
			if (!searchField.isEmpty()) {
				spellCheckFields.add(searchField);
			}
		}
		return new SpellCorrector(spellCheckFields.toArray(new String[spellCheckFields.size()]));
	}

	protected QueryBuilder buildTermQuery(final QueryStringTerm term, final Collection<String> searchFields) {
		final QueryStringQueryBuilder wordQuery = QueryBuilders
				.queryStringQuery(term.toQueryString())
				.fuzziness(Fuzziness.ZERO)
				.analyzer(analyzer);

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
	private LinkedHashMap<String, QueryStringTerm> getMatchingTerms(final Map<Float, WeightedWord> weightsPerTerm,
			final Bucket scoreBucket) {
		final LinkedHashMap<String, QueryStringTerm> termsOrdered = new LinkedHashMap<>();
		float additiveWeight = Float.parseFloat(scoreBucket.getKeyAsString());
		for (final Entry<Float, WeightedWord> weightPerTerm : weightsPerTerm.entrySet()) {
			if (weightPerTerm.getKey() <= additiveWeight) {
				additiveWeight -= weightPerTerm.getKey();
				termsOrdered.put(weightPerTerm.getValue().getWord(), weightPerTerm.getValue());
				if (additiveWeight == 0) {
					break;
				}
			}
		}
		return termsOrdered;
	}

	private void applyTermMatches(final List<QueryStringTerm> searchTerms,
			final Map<String, Set<String>> shingleSources,
			final PredictedQuery predictedQuery, final Map<String, WordAssociation> correctedWords) {
		int originalTermCount = 0;
		for (final QueryStringTerm term : searchTerms) {
			if (predictedQuery.termsUnique.containsKey(term.getWord())) {
				originalTermCount++;
				continue;
			}

			final Set<String> shinglesWithTerm = shingleSources.get(term.getWord());
			if (shingleSources != null && containsAny(predictedQuery.termsUnique.keySet(), shinglesWithTerm)) {
				originalTermCount++;
			}
			else {
				final WordAssociation correctedWord = correctedWords.get(term.getWord());
				predictedQuery.unknownTerms.add(correctedWord != null ? correctedWord : term);
			}
		}
		predictedQuery.containsAllTerms = originalTermCount == searchTerms.size();
		predictedQuery.originalTermCount = originalTermCount;
	}

	private Map<String, Set<String>> createOrderedShingles(final List<QueryStringTerm> searchWords) {
		final Map<String, Set<String>> shingles = new HashMap<>();

		for (int i = 0; i < searchWords.size() - 1; i++) {
			shingles.put(searchWords.get(i).getWord() + searchWords.get(i + 1).getWord(),
					Sets.newHashSet(searchWords.get(i).getWord(), searchWords.get(i + 1).getWord()));
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
