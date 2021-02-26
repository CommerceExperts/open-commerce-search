package de.cxp.ocs.smartsuggest.querysuggester.lucene;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.lucene.analysis.CharArraySet;

import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.SuggesterFactory;
import de.cxp.ocs.smartsuggest.querysuggester.modified.ModifiedTermsService;
import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class LuceneSuggesterFactory implements SuggesterFactory {

	@NonNull
	private final Path indexFolder;

	@Setter
	private Optional<MeterRegistryAdapter> metricsRegistry = Optional.empty();

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
						.orElse(null),
				metricsRegistry);

		final long start = System.currentTimeMillis();
		List<SuggestRecord> suggestRecords = suggestData.getSuggestRecords();
		Collections.sort(suggestRecords, Comparator.comparingDouble(SuggestRecord::getWeight).reversed());
		luceneQuerySuggester.index(suggestRecords).join();
		log.info("Indexing {} suggestions took: {}ms", suggestRecords.size(), System.currentTimeMillis() - start);

		return luceneQuerySuggester;
	}

}
