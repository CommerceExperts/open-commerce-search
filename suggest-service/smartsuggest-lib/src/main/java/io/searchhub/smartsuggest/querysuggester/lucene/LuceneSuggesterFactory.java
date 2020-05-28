package io.searchhub.smartsuggest.querysuggester.lucene;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.lucene.analysis.CharArraySet;

import io.searchhub.smartsuggest.querysuggester.QuerySuggester;
import io.searchhub.smartsuggest.querysuggester.SuggesterFactory;
import io.searchhub.smartsuggest.querysuggester.modified.ModifiedTermsService;
import io.searchhub.smartsuggest.spi.SuggestData;
import io.searchhub.smartsuggest.spi.SuggestRecord;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class LuceneSuggesterFactory implements SuggesterFactory {

	@NonNull
	private final Path indexFolder;

	@Override
	public QuerySuggester getSuggester(SuggestData suggestData) {
		LuceneQuerySuggester luceneQuerySuggester = new LuceneQuerySuggester(
				indexFolder,
				Optional.ofNullable(suggestData.getLocale()).orElse(Locale.ROOT),
				new ModifiedTermsService(
						Collections.emptyMap(),
						Collections.emptyMap()),
				Optional.ofNullable(suggestData.getWordsToIgnore())
						.map(sw -> new CharArraySet(sw, true))
						.orElse(null));

		final long start = System.currentTimeMillis();
		List<SuggestRecord> suggestRecords = suggestData.getSuggestRecords();
		Collections.sort(suggestRecords, Comparator.comparingDouble(SuggestRecord::getWeight).reversed());
		luceneQuerySuggester.index(suggestRecords).join();
		log.info("Indexing {} suggestions took: {}ms", suggestRecords.size(), System.currentTimeMillis() - start);

		return luceneQuerySuggester;
	}

}
