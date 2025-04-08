package de.cxp.ocs.smartsuggest.querysuggester.lucene;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import de.cxp.ocs.smartsuggest.util.FileUtils;
import org.apache.lucene.analysis.CharArraySet;

import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
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
public class LuceneSuggesterFactory implements SuggesterFactory<LuceneQuerySuggester> {

	private static final String                  FILENAME_SUGGEST_DATA = "suggest_data.ser";
	@NonNull
	private final        Path                    baseDirectory;
	private              CompletableFuture<Void> persistJobFuture      = null;

	private Optional<MeterRegistryAdapter> metricsRegistryAdapter = Optional.empty();
	private Iterable<Tag>                  tags;

	@Override
	public LuceneQuerySuggester getSuggester(SuggestData suggestData, SuggestConfig suggestConfig) {
		final Path indexFolder;
		try {
			indexFolder = Files.createDirectory(baseDirectory.resolve(String.valueOf(suggestData.getModificationTime())));
		}
		catch (IOException e) {
			throw new IllegalArgumentException("can't write into base directory '" + baseDirectory + "'", e);
		}

		LuceneQuerySuggester luceneQuerySuggester = new LuceneQuerySuggester(
				indexFolder,
				suggestConfig,
				new ModifiedTermsService(
						suggestData.getRelaxedQueries(),
						suggestData.getSharpenedQueries(),
						suggestConfig),
				Optional.ofNullable(suggestData.getWordsToIgnore())
						.map(sw -> new CharArraySet(sw, true))
						.orElse(null));

		persistJobFuture = CompletableFuture.runAsync(() -> persistNonIndexedData(indexFolder, suggestData, suggestConfig));

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

	private void persistNonIndexedData(Path indexFolder, SuggestData data, SuggestConfig config) {
		// store all but suggest-records
		SuggestData nonIndexedData = SuggestData.builder()
				.type(data.getType())
				.locale(data.getLocale())
				.modificationTime(data.getModificationTime())
				.sharpenedQueries(data.getSharpenedQueries())
				.relaxedQueries(data.getRelaxedQueries())
				.wordsToIgnore(data.getWordsToIgnore())
				.build();
		try {
			FileUtils.persistSerializable(indexFolder.resolve(FILENAME_SUGGEST_DATA), nonIndexedData);
			// TODO: check if suggest can be skipped and does not need to be serialized
			if (config != null) FileUtils.persistSerializable(indexFolder.resolve("suggest_config.ser"), config);
		}
		catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	@Override
	public Path persist(LuceneQuerySuggester querySuggester) throws IOException {
		querySuggester.commit();
		persistJobFuture.join();
		return baseDirectory;
	}

	@Override
	public LuceneQuerySuggester recover(Path archiveFolder) throws IOException {
		Path suggestDataFilePath = archiveFolder.resolve(FILENAME_SUGGEST_DATA);
		if (!Files.exists(suggestDataFilePath)) {
			throw new IllegalArgumentException("invalid index folder: " + archiveFolder + ". File '" + FILENAME_SUGGEST_DATA + "' does not exist!");
		}
		SuggestData suggestData = FileUtils.loadSerializable(suggestDataFilePath, SuggestData.class);

		Path indexFolder = baseDirectory.resolve(String.valueOf(suggestData.getModificationTime()));
		Files.createDirectory(indexFolder);
		if (!FileUtils.isEmptyDirectory(indexFolder)) {
			throw new IllegalStateException("data for that index-state ("+suggestData.getModificationTime()+") already exists!");
		}

		// TODO

		return null;
	}

	@Override
	public void instrument(Optional<MeterRegistryAdapter> metricsRegistryAdapter, Iterable<Tag> tags) {
		this.metricsRegistryAdapter = metricsRegistryAdapter;
		this.tags = tags;
	}

}
