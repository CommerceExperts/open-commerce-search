package io.searchhub.smartsuggest.querysuggester.lucene;

import static java.util.Collections.*;
import static org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester.PRESERVE_SEP;
import static org.apache.lucene.search.suggest.analyzing.BlendedInfixSuggester.DEFAULT_NUM_FACTOR;
import static org.apache.lucene.search.suggest.analyzing.FuzzySuggester.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchhub.smartsuggest.querysuggester.QueryIndexer;
import io.searchhub.smartsuggest.querysuggester.QuerySuggester;
import io.searchhub.smartsuggest.querysuggester.SuggestException;
import io.searchhub.smartsuggest.querysuggester.modified.ModifiedTermsService;
import io.searchhub.smartsuggest.spi.SuggestRecord;
import io.searchhub.smartsuggest.util.Util;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LuceneQuerySuggester implements QuerySuggester, QueryIndexer {

	public static final String	BEST_MATCHES_GROUP_NAME				= "best matches";
	public static final String	TYPO_MATCHES_GROUP_NAME				= "typo matches";
	public static final String	FUZZY_MATCHES_ONE_EDIT_GROUP_NAME	= "fuzzy matches with 1 edit";
	public static final String	FUZZY_MATCHES_TWO_EDITS_GROUP_NAME	= "fuzzy matches with 2 edits";
	public static final String	SHINGLE_MATCHES_GROUP_NAME			= "shingle matches";
	public static final String	RELAXED_GROUP_NAME					= "relaxed matches";
	public static final String	SHARPENED_GROUP_NAME				= "sharpened matches";

	private final AnalyzingInfixSuggester	infixSuggester;
	private final AnalyzingInfixSuggester	typoSuggester;
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
	private final Locale				locale;
	private final ModifiedTermsService	modifiedTermsService;

	private Instant lastIndexTime;
	private boolean alwaysDoFuzzy = Boolean.getBoolean("alwaysDoFuzzy");

	private static final Logger perfLog = LoggerFactory.getLogger("io.searchhub.suggest.performance");
	
	private volatile boolean isClosed = false;
	
	/**
	 * Constructor.
	 * 
	 * @param indexFolder
	 *        the parent folder for the specific suggesters
	 * @param locale
	 *        the locale of the client. Used to load the proper stopwords
	 * @param modifiedTermsService
	 *        service that provides mappings for modified terms
	 * @param stopWords
	 *        optional set of stopwords. may be null
	 */
	public LuceneQuerySuggester(@NonNull Path indexFolder, @NonNull Locale locale, @NonNull ModifiedTermsService modifiedTermsService, CharArraySet stopWords) {
		this.modifiedTermsService = modifiedTermsService;
		this.locale = locale;

		try {
			// TODO: extract a AnalyzerProviderInterface to make this customizable
			// that should be configurable rather than being programmable
			Analyzer basicIndexAnalyzer = setupBasicAnalyzer(true, stopWords);
			Analyzer basicQueryAnalyzer = setupBasicAnalyzer(false, stopWords);
			
			MMapDirectory infixDir = new MMapDirectory(indexFolder.resolve("infix"));
			// infixSuggester = new AnalyzingInfixSuggester(indexDir, basicIndexAnalyzer, basicQueryAnalyzer, AnalyzingInfixSuggester.DEFAULT_MIN_PREFIX_CHARS, false, false, AnalyzingInfixSuggester.DEFAULT_HIGHLIGHT);
			infixSuggester = new BlendedInfixSuggester(infixDir, basicIndexAnalyzer, basicQueryAnalyzer,
					AnalyzingInfixSuggester.DEFAULT_MIN_PREFIX_CHARS, BlendedInfixSuggester.BlenderType.CUSTOM, DEFAULT_NUM_FACTOR, false);
			closeables.add(infixSuggester);

			Analyzer basicIndexAnalyzer2 = setupBasicAnalyzer(true, stopWords);
			Analyzer basicQueryAnalyzer2 = setupBasicAnalyzer(false, stopWords);
			MMapDirectory infixDir2 = new MMapDirectory(indexFolder.resolve("typo"));
			typoSuggester = new AnalyzingInfixSuggester(infixDir2, basicIndexAnalyzer2, basicQueryAnalyzer2,
					AnalyzingInfixSuggester.DEFAULT_MIN_PREFIX_CHARS, false);
			closeables.add(typoSuggester);

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
	public List<Result> suggest(String term, final int maxResults, Set<String> groups) {
		if (isClosed) return Collections.emptyList();
		try {
			final List<Result> results = new ArrayList<>();
			final Set<String> uniqueQueries = new HashSet<>();
			final Set<BytesRef> contexts;
			if (groups != null && !groups.isEmpty()) {
				contexts = groups.stream()
						.map(group -> new BytesRef(group.getBytes(StandardCharsets.UTF_8)))
						.collect(Collectors.toSet());
			}
			else {
				contexts = null;
			}

			PerfResult perfResult = new PerfResult(term);
			
			if (modifiedTermsService.hasData()) {
				int resultCount = collectModifiedSuggestions(term, modifiedTermsService.getRelaxedTerm(term), uniqueQueries, maxResults, RELAXED_GROUP_NAME, results);
				perfResult.addStep("relaxedTerms", resultCount);
				
				resultCount = collectModifiedSuggestions(term, modifiedTermsService.getSharpenedTerm(term), uniqueQueries, maxResults, SHARPENED_GROUP_NAME, results);
				perfResult.addStep("sharpenedTerms", resultCount);
			}

			// lookup for best matches
			{
				int resultCount = collectSuggestions(term, contexts, infixSuggester, maxResults, uniqueQueries, maxResults, BEST_MATCHES_GROUP_NAME, results);
				perfResult.addStep("bestMatches", resultCount);
			}
					
			// lookup known typo variants
			if (uniqueQueries.size() < maxResults) {
				final int itemsToFetchTypos = maxResults - uniqueQueries.size();
				int resultCount = collectSuggestions(term, contexts, typoSuggester, itemsToFetchTypos, uniqueQueries, itemsToFetchTypos, TYPO_MATCHES_GROUP_NAME, results);
				perfResult.addStep("variantMatches", resultCount);
			}

			// fuzzy lookup with one edit
			if (term.length() >= DEFAULT_MIN_FUZZY_LENGTH && (alwaysDoFuzzy || uniqueQueries.isEmpty()) && uniqueQueries.size() < maxResults && contexts == null) {
				final int itemsToFetchOneEdit = maxResults - uniqueQueries.size();
				int resultCount = collectSuggestions(term, contexts, fuzzySuggesterOneEdit, itemsToFetchOneEdit, uniqueQueries, itemsToFetchOneEdit, FUZZY_MATCHES_ONE_EDIT_GROUP_NAME, results);
				perfResult.addStep("fuzzy1Matches", resultCount);
			}

			// fuzzy lookup with two edits
			if (term.length() >= DEFAULT_MIN_FUZZY_LENGTH && (alwaysDoFuzzy || uniqueQueries.isEmpty()) && uniqueQueries.size() < maxResults && contexts == null) {
				final int itemsToFetchTwoEdits = maxResults - uniqueQueries.size();
				int resultCount = collectSuggestions(term, contexts, fuzzySuggesterTwoEdits, itemsToFetchTwoEdits, uniqueQueries, itemsToFetchTwoEdits, FUZZY_MATCHES_TWO_EDITS_GROUP_NAME, results);
				perfResult.addStep("fuzzy2Matches", resultCount);
			}
			
			// lookup with shingles
			if ((alwaysDoFuzzy || uniqueQueries.isEmpty()) && uniqueQueries.size() < maxResults && contexts == null) {
				final int itemsToFetchShingles = maxResults - uniqueQueries.size();
				int resultCount = collectSuggestions(term, contexts, shingleSuggester, itemsToFetchShingles, uniqueQueries, itemsToFetchShingles, SHINGLE_MATCHES_GROUP_NAME, results);
				perfResult.addStep("shingleMatches",  resultCount);
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

			if (log.isDebugEnabled()) {
				int totalSuggestions = 0;
				for (Result result : results) {
					totalSuggestions += result.getSuggestions().size();
				}
				log.debug("Collected '{}' suggestions for term '{}'", totalSuggestions, term);
			}

			return results;
		}
		catch (Exception x) {
			throw new SuggestException("An error occurred while collecting the suggestions for '" + term + "'", x);
		}
	}

	private int collectSuggestions(String term, Set<BytesRef> contexts, Lookup suggester, int itemsToFetch,
			Set<String> uniqueQueries, int maxResults, String groupName, List<Result> results) throws IOException {
		final List<Lookup.LookupResult> lookupResults = suggester.lookup(term, contexts, false, itemsToFetch);

		final List<String> suggestions = getUniqueSuggestions(lookupResults, uniqueQueries, maxResults);

		if (!suggestions.isEmpty()) {
			sortSuggestions(suggestions, term, groupName);
			uniqueQueries.addAll(suggestions);
			results.add(new Result(groupName, suggestions));
			log.debug("Collected '{}' {} for term '{}': {}", suggestions.size(), groupName, term, suggestions);
			
		}
		return suggestions.size(); 
	}

	private int collectModifiedSuggestions(String term, List<String> suggestions,
			Set<String> uniqueQueries, int maxResults, String groupName,
			List<Result> results) {

		if (suggestions != null && !suggestions.isEmpty()) {
			suggestions = suggestions.subList(0, Math.min(suggestions.size(), maxResults));
			sortSuggestions(suggestions, term, groupName);
			uniqueQueries.addAll(suggestions);
			results.add(new Result(groupName, suggestions));
			log.debug("Collected '{}' {} for term '{}': {}", suggestions.size(), groupName, term, suggestions);
			return suggestions.size();
		}
		return 0;
	}

	private void sortSuggestions(List<String> suggestions, String term, String groupName) {
		if (FUZZY_MATCHES_ONE_EDIT_GROUP_NAME.equals(groupName) || FUZZY_MATCHES_TWO_EDITS_GROUP_NAME.equals(groupName)) {
			sortFuzzySuggestions(suggestions, term);
		}
	}

	private void sortFuzzySuggestions(List<String> suggestions, String term) {
		sort(suggestions, (s1, s2) -> {
			final double s1CommonChars = Util.commonChars(locale, s1, term);
			final double s2CommonChars = Util.commonChars(locale, s2, term);
			return Double.compare(s1CommonChars, s2CommonChars);
		});
	}

	private List<String> getUniqueSuggestions(List<Lookup.LookupResult> results, Set<String> uniqueQueries, int maxResults) {
		log.debug("Going to get the best matches for: {}", results);
		final List<String> bestMatches = results.stream()
				.map(this::getBestMatch)
				.filter(Objects::nonNull)
				.filter(bestMatch -> !uniqueQueries.contains(bestMatch))
				.distinct()
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
		CompletableFuture<Void> infixFuture = CompletableFuture.runAsync(indexAsync(infixSuggester, suggestions, false));
		CompletableFuture<Void> typoInfixFuture = CompletableFuture.runAsync(indexAsync(typoSuggester, suggestions, true));
		CompletableFuture<Void> fuzzyShortFuture = CompletableFuture.runAsync(indexAsync(fuzzySuggesterOneEdit, suggestions, false));
		CompletableFuture<Void> fuzzyLongFuture = CompletableFuture.runAsync(indexAsync(fuzzySuggesterTwoEdits, suggestions, false));
		CompletableFuture<Void> shingleFuture = CompletableFuture.runAsync(indexAsync(shingleSuggester, suggestions, true));
		return CompletableFuture
				.allOf(infixFuture, typoInfixFuture, fuzzyShortFuture, fuzzyLongFuture, shingleFuture)
				.thenRun(() -> lastIndexTime = Instant.now());
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

	/**
	 * @see SuggestionIterator#payload()
	 */
	private String getBestMatch(Lookup.LookupResult result) {
		BytesRef payload = result.payload;
		if (payload != null) {
			String bestMatch = new String(payload.bytes, StandardCharsets.UTF_8);
			return bestMatch;
		}
		else {
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
}
