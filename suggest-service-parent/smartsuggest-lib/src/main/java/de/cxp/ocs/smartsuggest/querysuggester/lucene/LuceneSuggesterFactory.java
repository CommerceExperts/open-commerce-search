package de.cxp.ocs.smartsuggest.querysuggester.lucene;

import de.cxp.ocs.smartsuggest.monitoring.MeterRegistryAdapter;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.SuggesterFactory;
import de.cxp.ocs.smartsuggest.querysuggester.modified.ModifiedTermsService;
import de.cxp.ocs.smartsuggest.spi.IndexArchive;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import de.cxp.ocs.smartsuggest.util.FileUtils;
import io.micrometer.core.instrument.Tag;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.CharArraySet;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
public class LuceneSuggesterFactory implements SuggesterFactory<LuceneQuerySuggester> {

	private static final String FILENAME_SUGGEST_DATA = "suggest_data.ser";

	@NonNull
	private final Path                    baseDirectory;
	private       CompletableFuture<Void> persistJobFuture = null;

	private Optional<MeterRegistryAdapter> metricsRegistryAdapter = Optional.empty();
	private Iterable<Tag>                  tags;

	@Override
	public void instrument(Optional<MeterRegistryAdapter> metricsRegistryAdapter, Iterable<Tag> tags) {
		this.metricsRegistryAdapter = metricsRegistryAdapter;
		this.tags = tags;
	}

	@Override
	public LuceneQuerySuggester getSuggester(SuggestData suggestData, SuggestConfig suggestConfig) {
		final Path indexFolder = prepareIndexFolder(suggestData.getModificationTime());

		LuceneQuerySuggester luceneQuerySuggester = initSuggester(suggestData, suggestConfig, indexFolder);
		indexSuggestRecords(suggestData, luceneQuerySuggester);
		// already start persistence of all data that won't be indexed
		persistJobFuture = CompletableFuture.runAsync(() -> persistNonIndexedData(indexFolder, suggestData));

		return luceneQuerySuggester;
	}

	private Path prepareIndexFolder(Long modTime) {
		Path indexFolder = baseDirectory.resolve(String.valueOf(modTime));
		try {
			Files.createDirectories(indexFolder);
		}
		catch (IOException ioe) {
			throw new IllegalArgumentException("base directory " + baseDirectory + " is not writable", ioe);
		}
		if (!FileUtils.isEmptyDirectory(indexFolder)) {
			throw new IllegalStateException("data for that index-state (" + modTime + ") already exists!");
		}
		return indexFolder;
	}

	private LuceneQuerySuggester initSuggester(SuggestData suggestData, SuggestConfig suggestConfig, Path indexFolder) {
		var luceneQuerySuggester = new LuceneQuerySuggester(
				indexFolder,
				suggestConfig,
				new ModifiedTermsService(
						suggestData.getRelaxedQueries(),
						suggestData.getSharpenedQueries(),
						suggestConfig),
				Optional.ofNullable(suggestData.getWordsToIgnore())
						.map(sw -> new CharArraySet(sw, true))
						.orElse(null),
				suggestData.getModificationTime());
		if (metricsRegistryAdapter.isPresent()) {
			luceneQuerySuggester.instrument(metricsRegistryAdapter, tags);
		}
		return luceneQuerySuggester;
	}

	private static void indexSuggestRecords(SuggestData suggestData, LuceneQuerySuggester luceneQuerySuggester) {
		final long start = System.currentTimeMillis();
		Iterable<SuggestRecord> suggestRecords = suggestData.getSuggestRecords();
		if (suggestRecords instanceof List<SuggestRecord> suggestRecordList) {
			suggestRecordList.sort(Comparator.comparingDouble(SuggestRecord::getWeight).reversed());
		}
		luceneQuerySuggester.index(suggestRecords, suggestData.getModificationTime()).join();
		log.info("Indexing {} suggestions took: {}ms", luceneQuerySuggester.recordCount(), System.currentTimeMillis() - start);
	}

	private void persistNonIndexedData(Path indexFolder, SuggestData data) {
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
			// TODO: check if suggest can be skipped as it can't be serialized without changing its properties
			//if (config != null) FileUtils.persistSerializable(indexFolder.resolve(FILENAME_SUGGEST_CONFIG), config);
		}
		catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	@Override
	public IndexArchive createArchive(QuerySuggester querySuggester) throws IOException {
		LuceneQuerySuggester luceneSuggester = (LuceneQuerySuggester) querySuggester;
		final long start = System.currentTimeMillis();
		luceneSuggester.commit();
		persistJobFuture.join();
		File tarGzFile = FileUtils.packArchive(luceneSuggester.getIndexFolder(), "suggest-index-" + luceneSuggester.getIndexModTime().toEpochMilli());
		log.info("suggester persisted to {} in {}ms", tarGzFile, System.currentTimeMillis() - start);
		return new IndexArchive(tarGzFile, luceneSuggester.getIndexModTime().toEpochMilli());
	}

	@Override
	public LuceneQuerySuggester recover(IndexArchive archive, SuggestConfig suggestConfig) throws IOException {
		final long start = System.currentTimeMillis();
		Path indexFolder = prepareIndexFolder(archive.dataModificationTime());
		FileUtils.unpackArchive(archive, indexFolder);
		SuggestData suggestData = FileUtils.loadSerializable(indexFolder.resolve(FILENAME_SUGGEST_DATA), SuggestData.class);
		LuceneQuerySuggester suggester = initSuggester(suggestData, suggestConfig, indexFolder);
		assert suggester.isReady();
		log.info("recovered LuceneQuerySuggester from {} with {} records in {}ms", archive, suggester.recordCount(), System.currentTimeMillis() - start);
		return suggester;
	}

}
