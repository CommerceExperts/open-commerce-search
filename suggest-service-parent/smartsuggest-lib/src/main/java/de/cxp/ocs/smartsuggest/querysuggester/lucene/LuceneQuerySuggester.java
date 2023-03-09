package de.cxp.ocs.smartsuggest.querysuggester.lucene;

import static java.util.Collections.emptyList;
import static org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester.PRESERVE_SEP;
import static org.apache.lucene.search.suggest.analyzing.BlendedInfixSuggester.DEFAULT_NUM_FACTOR;
import static org.apache.lucene.search.suggest.analyzing.FuzzySuggester.DEFAULT_MIN_FUZZY_LENGTH;
import static org.apache.lucene.search.suggest.analyzing.FuzzySuggester.DEFAULT_TRANSPOSITIONS;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.analyzing.AnalyzingInfixSuggester;
import org.apache.lucene.search.suggest.analyzing.BlendedInfixSuggester;
import org.apache.lucene.search.suggest.analyzing.FuzzySuggester;
import org.apache.lucene.search.suggest.analyzing.SuggestStopFilter;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.RamUsageEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cxp.ocs.smartsuggest.monitoring.Instrumentable;
import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.querysuggester.QueryIndexer;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.SuggestException;
import de.cxp.ocs.smartsuggest.querysuggester.Suggestion;
import de.cxp.ocs.smartsuggest.querysuggester.modified.ModifiedTermsService;
import de.cxp.ocs.smartsuggest.spi.CommonPayloadFields;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig.SortStrategy;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import de.cxp.ocs.smartsuggest.util.Util;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LuceneQuerySuggester implements QuerySuggester, QueryIndexer, Accountable, Instrumentable {

	/**
	 * Use {@link CommonPayloadFields#PAYLOAD_LABEL_KEY} instead.
	 */
	@Deprecated
	public static final String	PAYLOAD_LABEL_KEY		= "meta.label";
	/**
	 * Use {@link CommonPayloadFields#PAYLOAD_GROUPMATCH_KEY} instead.
	 */
	@Deprecated
	public static final String	PAYLOAD_GROUPMATCH_KEY	= "meta.matchGroupName";

	public static final String	BEST_MATCHES_GROUP_NAME				= "best matches";
	public static final String	TYPO_MATCHES_GROUP_NAME				= "secondary matches";
	public static final String	FUZZY_MATCHES_ONE_EDIT_GROUP_NAME	= "fuzzy matches with 1 edit";
	public static final String	FUZZY_MATCHES_TWO_EDITS_GROUP_NAME	= "fuzzy matches with 2 edits";
	public static final String	SHINGLE_MATCHES_GROUP_NAME			= "shingle matches";
	public static final String	RELAXED_GROUP_NAME					= "relaxed matches";
	public static final String	SHARPENED_GROUP_NAME				= "sharpened matches";

	private static final String METRICS_PREFIX = Util.APP_NAME + ".lucene_suggester";

	private static final Logger perfLog = LoggerFactory.getLogger("de.cxp.ocs.smartsuggest.performance");

	private final AnalyzingInfixSuggester	primarySuggester;
	private final AnalyzingInfixSuggester	secondarySuggester;
	private final AnalyzingInfixSuggester	shingleSuggester;

	/**
	 * A fuzzy suggester that is used for search terms shorter than or equal to
	 * 6 characters.
	 * It uses fuzziness=1.
	 */
	private final FuzzySuggester fuzzySuggesterOneEdit;

	/**
	 * A fuzzy suggester that is used for search terms longer than 5 characters
	 * It uses fuzziness=2.
	 */
	private final FuzzySuggester		fuzzySuggesterTwoEdits;
	private final List<Closeable>		closeables	= new ArrayList<>();
	private final SuggestConfig			suggestConfig;
	private final ModifiedTermsService	modifiedTermsService;

	private Instant	lastIndexTime;
	private long	recordCount		= 0;
	private long	memUsageBytes	= 0;

	private volatile boolean isClosed = false;

	/**
	 * Constructor.
	 * 
	 * @param indexFolder
	 *        the parent folder for the specific suggesters
	 * @param suggestConfig
	 *        the full suggest configuration
	 * @param modifiedTermsService
	 *        service that provides mappings for modified terms
	 * @param stopWords
	 *        optional set of stopwords. may be null
	 */
	public LuceneQuerySuggester(@NonNull Path indexFolder, @NonNull SuggestConfig suggestConfig, @NonNull ModifiedTermsService modifiedTermsService, CharArraySet stopWords) {
		this.modifiedTermsService = modifiedTermsService;
		this.suggestConfig = suggestConfig;

		try {
			// TODO: extract a AnalyzerProviderInterface to make this
			// customizable
			// that should be configurable rather than being programmable
			Analyzer basicIndexAnalyzer = setupBasicAnalyzer(true, stopWords);
			Analyzer basicQueryAnalyzer = setupBasicAnalyzer(false, stopWords);

			MMapDirectory infixDir = new MMapDirectory(indexFolder.resolve("infix"));
			// infixSuggester = new AnalyzingInfixSuggester(indexDir,
			// basicIndexAnalyzer, basicQueryAnalyzer,
			// AnalyzingInfixSuggester.DEFAULT_MIN_PREFIX_CHARS, false, false,
			// AnalyzingInfixSuggester.DEFAULT_HIGHLIGHT);
			primarySuggester = new BlendedInfixSuggester(infixDir, basicIndexAnalyzer, basicQueryAnalyzer,
					AnalyzingInfixSuggester.DEFAULT_MIN_PREFIX_CHARS, BlendedInfixSuggester.BlenderType.CUSTOM, DEFAULT_NUM_FACTOR, false);
			closeables.add(primarySuggester);

			Analyzer basicIndexAnalyzer2 = setupBasicAnalyzer(true, stopWords);
			Analyzer basicQueryAnalyzer2 = setupBasicAnalyzer(false, stopWords);
			MMapDirectory infixDir2 = new MMapDirectory(indexFolder.resolve("typo"));
			secondarySuggester = new AnalyzingInfixSuggester(infixDir2, basicIndexAnalyzer2, basicQueryAnalyzer2,
					AnalyzingInfixSuggester.DEFAULT_MIN_PREFIX_CHARS, false);
			closeables.add(secondarySuggester);

			final Analyzer shingleIndexAnalyzer = setupShingleAnalyzer(true, stopWords);
			final Analyzer shingleQueryAnalyzer = setupShingleAnalyzer(false, stopWords);
			MMapDirectory shingleDir = new MMapDirectory(indexFolder.resolve("shingle"));
			shingleSuggester = new BlendedInfixSuggester(shingleDir, shingleIndexAnalyzer, shingleQueryAnalyzer,
					AnalyzingInfixSuggester.DEFAULT_MIN_PREFIX_CHARS,
					BlendedInfixSuggester.BlenderType.POSITION_RECIPROCAL, DEFAULT_NUM_FACTOR, null, false, false, false);
			closeables.add(shingleSuggester);

			fuzzySuggesterOneEdit = createFuzzySuggester(indexFolder, "Short", basicIndexAnalyzer, basicQueryAnalyzer, 1);
			fuzzySuggesterTwoEdits = createFuzzySuggester(indexFolder, "Long", basicIndexAnalyzer, basicQueryAnalyzer, 2);

			index(emptyList()).join();
		}
		catch (IOException iox) {
			throw new SuggestException("An error occurred while initializing the QuerySuggester", iox);
		}
	}

	@Override
	public void instrument(Optional<MeterRegistryAdapter> metricsRegistryAdapter, Iterable<Tag> tags) {
		metricsRegistryAdapter.ifPresent(reg -> this.addSensors(reg.getMetricsRegistry(), tags));
	}

	private void addSensors(MeterRegistry reg, Iterable<Tag> tags) {
		reg.gauge(METRICS_PREFIX + ".record_count", tags, this, me -> me.recordCount);
		reg.gauge(METRICS_PREFIX + ".estimated_memusage_bytes", tags, this, me -> me.memUsageBytes);
		reg.more().counter(METRICS_PREFIX + ".last_index_timestamp_seconds", tags, this,
				me -> (me.lastIndexTime == null ? -1 : me.lastIndexTime.getEpochSecond()));
	}

	@Override
	public boolean isReady() {
		return lastIndexTime != null;
	}

	private FuzzySuggester createFuzzySuggester(Path indexFolder, String name,
			Analyzer indexAnalyzer, Analyzer queryAnalyzer, int maxEdits) throws IOException {
		MMapDirectory fuzzyDirectory = new MMapDirectory(indexFolder.resolve("fuzzy" + name));
		return new FuzzySuggester(fuzzyDirectory, "fuzzy" + name + "-suggest", indexAnalyzer, queryAnalyzer,
				PRESERVE_SEP, 256, -1, true, maxEdits, DEFAULT_TRANSPOSITIONS,
				0, DEFAULT_MIN_FUZZY_LENGTH, true);
	}

	private Analyzer setupBasicAnalyzer(boolean forIndexing, CharArraySet stopWordSet) {
		final Analyzer analyzer = new Analyzer() {

			@Override
			protected TokenStreamComponents createComponents(String fieldName) {
				Tokenizer tokenizer = new StandardTokenizer();
				TokenStream tokenStream = getCommonTokenStream(forIndexing, stopWordSet, tokenizer);

				return new TokenStreamComponents(tokenizer, tokenStream);
			}

			@Override
			protected TokenStream normalize(String fieldName, TokenStream in) {
				return new LowerCaseFilter(super.normalize(fieldName, in));
			}
		};
		closeables.add(analyzer);
		return analyzer;
	}

	private Analyzer setupShingleAnalyzer(boolean forIndexing, CharArraySet stopWordSet) {
		final Analyzer analyzer = new Analyzer() {

			@Override
			protected TokenStreamComponents createComponents(String fieldName) {
				Tokenizer tokenizer = new StandardTokenizer();
				TokenStream tokenStream = getCommonTokenStream(forIndexing, stopWordSet, tokenizer);
				tokenStream = new ShingleFilter(tokenStream, 3, 4);

				return new TokenStreamComponents(tokenizer, tokenStream);
			}

			@Override
			protected TokenStream normalize(String fieldName, TokenStream in) {
				return new LowerCaseFilter(super.normalize(fieldName, in));
			}
		};
		closeables.add(analyzer);
		return analyzer;
	}

	private TokenStream getCommonTokenStream(boolean forIndexing, CharArraySet stopWordSet, Tokenizer tokenizer) {

		TokenStream tokenStream = tokenizer;
		if (stopWordSet != null) {
			if (forIndexing) {
				tokenStream = new StopFilter(tokenizer, stopWordSet);
			}
			else {
				tokenStream = new SuggestStopFilter(tokenizer, stopWordSet);
			}
		}
		tokenStream = new LowerCaseFilter(tokenStream);
		tokenStream = new ASCIIFoldingFilter(tokenStream);
		return tokenStream;
	}

	@Override
	public List<Suggestion> suggest(String term, final int maxResults, Set<String> tags) {
		if (isClosed) return Collections.emptyList();
		try {
			final List<Suggestion> results = new ArrayList<>();
			final Set<String> uniqueQueries = new HashSet<>();
			final Set<BytesRef> contexts;
			if (tags != null && !tags.isEmpty()) {
				contexts = tags.stream()
						.map(group -> new BytesRef(group.getBytes(StandardCharsets.UTF_8)))
						.collect(Collectors.toSet());
			}
			else {
				contexts = null;
			}

			PerfResult perfResult = new PerfResult(term);

			if (modifiedTermsService.hasData()) {
				int resultCount = collectModifiedSuggestions(term, modifiedTermsService.getSharpenedTerm(term), uniqueQueries, maxResults, SHARPENED_GROUP_NAME, results);
				perfResult.addStep("sharpenedTerms", resultCount);
			}

			// lookup for best matches
			{
				int resultCount = collectSuggestions(term, contexts, primarySuggester, maxResults, uniqueQueries, maxResults, BEST_MATCHES_GROUP_NAME, results);
				perfResult.addStep("bestMatches", resultCount);
			}

			// lookup known typo variants
			if (uniqueQueries.size() < maxResults) {
				final int itemsToFetchTypos = maxResults - uniqueQueries.size();
				int resultCount = collectSuggestions(term, contexts, secondarySuggester, itemsToFetchTypos, uniqueQueries, itemsToFetchTypos, TYPO_MATCHES_GROUP_NAME, results);
				if (SortStrategy.PrimaryAndSecondaryByWeight.equals(suggestConfig.getSortStrategy())) {
					Collections.sort(results, Util.getDefaultComparator(suggestConfig.locale, term));
				}
				perfResult.addStep("variantMatches", resultCount);
			}

			// fuzzy lookup with one edit
			if (term.length() >= DEFAULT_MIN_FUZZY_LENGTH && (suggestConfig.isAlwaysDoFuzzy() || uniqueQueries.isEmpty()) && uniqueQueries.size() < maxResults
					&& contexts == null) {
				final int itemsToFetchOneEdit = maxResults - uniqueQueries.size();
				int resultCount = collectFuzzySuggestions(term, contexts, fuzzySuggesterOneEdit, itemsToFetchOneEdit, uniqueQueries, itemsToFetchOneEdit,
						FUZZY_MATCHES_ONE_EDIT_GROUP_NAME, results);
				perfResult.addStep("fuzzy1Matches", resultCount);
			}

			// fuzzy lookup with two edits
			if (term.length() >= DEFAULT_MIN_FUZZY_LENGTH && (suggestConfig.isAlwaysDoFuzzy() || uniqueQueries.isEmpty()) && uniqueQueries.size() < maxResults
					&& contexts == null) {
				final int itemsToFetchTwoEdits = maxResults - uniqueQueries.size();
				int resultCount = collectFuzzySuggestions(term, contexts, fuzzySuggesterTwoEdits, itemsToFetchTwoEdits, uniqueQueries, itemsToFetchTwoEdits,
						FUZZY_MATCHES_TWO_EDITS_GROUP_NAME, results);
				perfResult.addStep("fuzzy2Matches", resultCount);
			}

			// lookup with shingles
			if ((suggestConfig.isAlwaysDoFuzzy() || uniqueQueries.isEmpty()) && uniqueQueries.size() < maxResults && contexts == null) {
				final int itemsToFetchShingles = maxResults - uniqueQueries.size();
				int resultCount = collectSuggestions(term, contexts, shingleSuggester, itemsToFetchShingles, uniqueQueries, itemsToFetchShingles, SHINGLE_MATCHES_GROUP_NAME,
						results);
				perfResult.addStep("shingleMatches", resultCount);
			}

			if (modifiedTermsService.hasData() && uniqueQueries.size() < maxResults) {
				final int itemsToFetchShingles = maxResults - uniqueQueries.size();
				int resultCount = collectModifiedSuggestions(term, modifiedTermsService.getRelaxedTerm(term), uniqueQueries, itemsToFetchShingles, RELAXED_GROUP_NAME, results);
				perfResult.addStep("relaxedTerms", resultCount);
			}

			perfResult.stop();
			if (perfResult.getTotalTime().getTime() > 200L) {
				perfLog.warn(perfResult.toString());
			}
			else if (perfResult.getTotalTime().getTime() > 100L) {
				perfLog.info(perfResult.toString());
			}
			else {
				perfLog.trace(perfResult.toString());
			}

			log.debug("Collected '{}' suggestions for term '{}'", results.size(), term);
			return results;
		}
		catch (Exception x) {
			throw new SuggestException("An error occurred while collecting the suggestions for '" + term + "'", x);
		}
	}

	/**
	 * <p>
	 * Use this method for fuzzy suggesters that have only the primary-text /
	 * labels indexed. It will fetch more suggestions and reorder them to get
	 * terms with better prefix- and common-chars matches.
	 * </p>
	 * <p>
	 * ATTENTION: This method MUST only be used for suggesters that only have
	 * the primary text indexed, because we use the indexed text to check for
	 * prefix match.
	 * </p>
	 * 
	 * @param term
	 * @param contexts
	 * @param suggester
	 * @param itemsToFetch
	 * @param uniqueQueries
	 * @param maxResults
	 * @param groupName
	 * @param results
	 * @return
	 * @throws IOException
	 */
	private int collectFuzzySuggestions(String term, Set<BytesRef> contexts, Lookup suggester, final int itemsToFetch, Set<String> uniqueQueries,
			int maxResults, String groupName, List<Suggestion> results) throws IOException {

		final List<Lookup.LookupResult> lookupResults = suggester.lookup(term, contexts, false, itemsToFetch + uniqueQueries.size());

		List<Suggestion> suggestions = lookupResults.stream()
				.filter(Objects::nonNull)
				// do not deserialize the hundreds of results yet, instead use
				// the LookupResult.key (=primary text / =label?) and s.value
				// (=weight) directly.
				// Because of that however, we can only use that method only for
				// suggesters that have only indexed the primary texts = label!
				.filter(s -> !uniqueQueries.contains(s.key))
				.collect(Util.getTopKFuzzySuggestionCollector(itemsToFetch + uniqueQueries.size(), suggestConfig.locale, term))
				.stream()
				.map(this::getBestMatch)
				.filter(Objects::nonNull)
				.filter(s -> uniqueQueries.add(s.getLabel()))
				.limit(itemsToFetch)
				.peek(s -> withPayloadEntry(s, CommonPayloadFields.PAYLOAD_GROUPMATCH_KEY, groupName))
				.collect(Collectors.toList());

		if (!suggestions.isEmpty()) {
			results.addAll(suggestions);
			log.debug("Collected '{}' {} for term '{}': {}", suggestions.size(), groupName, term, suggestions);
		}
		return suggestions.size();
	}

	private int collectSuggestions(String term, Set<BytesRef> contexts, Lookup suggester, int itemsToFetch,
			Set<String> uniqueQueries, int maxResults, String groupName, List<Suggestion> results) throws IOException {
		final List<Lookup.LookupResult> lookupResults = suggester.lookup(term, contexts, false, itemsToFetch + uniqueQueries.size());

		final List<Suggestion> suggestions = getUniqueSuggestions(lookupResults, uniqueQueries, maxResults);
		suggestions.forEach(s -> {
			withPayloadEntry(s, CommonPayloadFields.PAYLOAD_GROUPMATCH_KEY, groupName);
		});

		if (!suggestions.isEmpty()) {
			results.addAll(suggestions);
			log.debug("Collected '{}' {} for term '{}': {}", suggestions.size(), groupName, term, suggestions);

		}
		return suggestions.size();
	}

	private int collectModifiedSuggestions(String term, List<String> modifiedSuggestions,
			Set<String> uniqueQueries, int maxResults, String groupName,
			List<Suggestion> results) {

		if (modifiedSuggestions != null && !modifiedSuggestions.isEmpty()) {
			long count = modifiedSuggestions.stream()
					.filter(uniqueQueries::add)
					// TODO: figure out, which are better matching before
					// truncating
					.limit(maxResults)
					.map(l -> withPayloadEntry(new Suggestion(l), CommonPayloadFields.PAYLOAD_GROUPMATCH_KEY, groupName))
					.peek(results::add)
					.count();

			log.debug("Collected '{}' {} for term '{}'", count, groupName, term);
			return (int) count;
		}
		return 0;
	}

	private static Suggestion withPayloadEntry(Suggestion s, String key, String value) {
		if (s.getPayload() == null) {
			s.setPayload(Collections.singletonMap(key, value));
		}
		else if (!(s.getPayload() instanceof HashMap<?, ?>)) {
			HashMap<String, String> payload = new HashMap<>(s.getPayload());
			payload.put(key, value);
			s.setPayload(payload);
		}
		else {
			s.getPayload().put(key, value);
		}
		return s;
	}

	private List<Suggestion> getUniqueSuggestions(List<Lookup.LookupResult> results, Set<String> uniqueQueries, int maxResults) {
		log.debug("Going to get the best matches for: {}", results);
		final List<Suggestion> bestMatches = results.stream()
				.map(this::getBestMatch)
				.filter(Objects::nonNull)
				.filter(bestMatch -> uniqueQueries.add(bestMatch.getLabel()))
				.limit(maxResults)
				.collect(Collectors.toList());
		log.debug("The best matches are: {}", bestMatches);
		return bestMatches;
	}

	@Override
	public Instant getLastIndexTime() {
		return lastIndexTime;
	}

	@Override
	public CompletableFuture<Void> index(Iterable<SuggestRecord> suggestions) {
		CompletableFuture<Void> infixFuture = CompletableFuture.runAsync(indexAsync(primarySuggester, suggestions, false));
		CompletableFuture<Void> typoInfixFuture = CompletableFuture.runAsync(indexAsync(secondarySuggester, suggestions, true));
		CompletableFuture<Void> fuzzyShortFuture = CompletableFuture.runAsync(indexAsync(fuzzySuggesterOneEdit, suggestions, false));
		CompletableFuture<Void> fuzzyLongFuture = CompletableFuture.runAsync(indexAsync(fuzzySuggesterTwoEdits, suggestions, false));
		CompletableFuture<Void> shingleFuture = CompletableFuture.runAsync(indexAsync(shingleSuggester, suggestions, true));
		return CompletableFuture
				.allOf(infixFuture, typoInfixFuture, fuzzyShortFuture, fuzzyLongFuture, shingleFuture)
				.thenRun(() -> {
					lastIndexTime = Instant.now();
					recordCount = getRecordCount(suggestions);
					memUsageBytes = ramBytesUsed();
				});
	}

	private long getRecordCount(Iterable<SuggestRecord> suggestions) {
		if (suggestions instanceof Collection<?>) {
			return ((Collection<?>) suggestions).size();
		}
		else {
			try {
				return primarySuggester.getCount();
			}
			catch (IOException e) {
				return StreamSupport.stream(suggestions.spliterator(), false).count();
			}
		}
	}

	private Runnable indexAsync(Lookup lookup, Iterable<SuggestRecord> suggestions, boolean useVariant) {
		return () -> {
			try {
				SuggestionIterator suggestionIterator;
				if (useVariant) {
					suggestionIterator = new SuggestionVariantIterator(suggestions.iterator());
				}
				else {
					suggestionIterator = new SuggestionBestMatchIterator(suggestions.iterator());
				}
				lookup.build(suggestionIterator);
			}
			catch (IOException iox) {
				throw new UncheckedIOException(iox);
			}
		};
	}

	private int deserializationFailLogCount = 0;

	/**
	 * @see SuggestionIterator#payload()
	 */
	private Suggestion getBestMatch(Lookup.LookupResult result) {
		try {
			Map<String, String> payload = SerializationUtils.deserialize(result.payload.bytes);
			String label = payload.get(CommonPayloadFields.PAYLOAD_LABEL_KEY);
			if (label == null) label = result.key.toString();
			return new Suggestion(label)
					.setPayload(payload)
					.setWeight(result.value)
					.setContext(result.contexts);
		}
		catch (Exception e) {
			if (deserializationFailLogCount % 100 == 0) {
				log.error("failed to deserialize LookupResult for key {} ({}th time)", result.key, deserializationFailLogCount, e);
			}
			deserializationFailLogCount++;
			return null;
		}
	}

	@Override
	public void close() throws Exception {
		destroy();
	}

	@Override
	public void destroy() {
		isClosed = true;
		for (Closeable closeable : closeables) {
			try {
				closeable.close();
			}
			catch (Exception x) {
				log.error("An error occurred while closing '{}'", closeable, x);
			}
		}
	}

	@Override
	public long ramBytesUsed() {
		long mySize = RamUsageEstimator.shallowSizeOf(this);
		mySize += RamUsageEstimator.sizeOf(primarySuggester);
		mySize += RamUsageEstimator.sizeOf(secondarySuggester);
		mySize += RamUsageEstimator.sizeOf(shingleSuggester);
		mySize += RamUsageEstimator.sizeOf(fuzzySuggesterOneEdit);
		mySize += RamUsageEstimator.sizeOf(fuzzySuggesterTwoEdits);
		mySize += RamUsageEstimator.sizeOf(modifiedTermsService);
		return mySize;
	}

	@Override
	public long recordCount() {
		try {
			return primarySuggester.getCount();
		}
		catch (IOException e) {
			log.warn("IOException when retrieving count of infixSuggester: " + e.getMessage());
			return -1;
		}
	}
}
