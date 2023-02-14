package de.cxp.ocs.smartsuggest.querysuggester.lucene;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.apache.lucene.analysis.CharArraySet;

import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.SuggesterFactory;
import de.cxp.ocs.smartsuggest.querysuggester.modified.ModifiedTermsService;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import io.micrometer.core.instrument.Tag;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class LuceneSuggesterFactory implements SuggesterFactory {

	@NonNull
	private final Path indexFolder;

	private Optional<MeterRegistryAdapter>	metricsRegistryAdapter	= Optional.empty();
	private Iterable<Tag>					tags;

	@Override
	public QuerySuggester getSuggester(SuggestData suggestData, SuggestConfig suggestConfig) {
		LuceneQuerySuggester luceneQuerySuggester = new LuceneQuerySuggester(
				indexFolder,
				suggestConfig,
				new ModifiedTermsService(
						suggestData.getRelaxedQueries(),
						suggestData.getSharpenedQueries()),
				Optional.ofNullable(suggestData.getWordsToIgnore())
						.map(sw -> new CharArraySet(sw, true))
						.orElse(null));

		if (metricsRegistryAdapter.isPresent()) {
			luceneQuerySuggester.instrument(metricsRegistryAdapter, tags);
		}

		final long start = System.currentTimeMillis();
		Iterable<SuggestRecord> suggestRecords = suggestData.getSuggestRecords();
		if (suggestRecords instanceof List) {
			Collections.sort((List<SuggestRecord>) suggestRecords, Comparator.comparingDouble(SuggestRecord::getWeight).reversed());
		}
		luceneQuerySuggester.index(suggestRecords).join();
		log.info("Indexing {} suggestions took: {}ms", luceneQuerySuggester.recordCount(), System.currentTimeMillis() - start);

		return luceneQuerySuggester;
	}

	@Override
	public void instrument(Optional<MeterRegistryAdapter> metricsRegistryAdapter, Iterable<Tag> tags) {
		this.metricsRegistryAdapter = metricsRegistryAdapter;
		this.tags = tags;
	}

}
