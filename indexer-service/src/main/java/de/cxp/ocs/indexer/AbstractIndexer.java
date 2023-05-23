package de.cxp.ocs.indexer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import de.cxp.ocs.api.indexer.FullIndexationService;
import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.api.indexer.UpdateIndexService;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.model.index.BulkImportData;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.preprocessor.CombiFieldBuilder;
import de.cxp.ocs.spi.indexer.DocumentPostProcessor;
import de.cxp.ocs.spi.indexer.DocumentPreProcessor;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractIndexer implements FullIndexationService, UpdateIndexService {

	@NonNull
	private final List<DocumentPreProcessor> dataPreProcessors;

	@Getter(value = AccessLevel.PROTECTED)
	@NonNull
	final FieldConfigIndex fieldConfIndex;

	private final CombiFieldBuilder combiFieldBuilder;

	private final IndexItemConverter indexItemConverter;

	public AbstractIndexer(
			@NonNull List<DocumentPreProcessor> dataPreProcessors,
			@NonNull List<DocumentPostProcessor> postProcessors,
			@NonNull FieldConfigIndex fieldConfIndex) {
		this.dataPreProcessors = dataPreProcessors;
		this.fieldConfIndex = fieldConfIndex;
		combiFieldBuilder = new CombiFieldBuilder(fieldConfIndex.getFieldsByType(FieldType.COMBI));
		indexItemConverter = new IndexItemConverter(fieldConfIndex, postProcessors);
	}

	@Override
	public ImportSession startImport(String indexName, String locale) throws IllegalStateException {
		if (!indexName.equals(indexName.toLowerCase(LocaleUtils.toLocale(locale)))) {
			throw new IllegalArgumentException(String.format("Invalid index name [%s], must be lowercase", indexName));
		}
		if (isImportRunning(indexName)) {
			log.warn("Another import for index {} is already running! Will start a new one never the less...", indexName);
			CompletableFuture.runAsync(() -> this.cleanupAbandonedImports(indexName, locale));
		}

		try {
			log.info("starting import session for index {} with locale {}", indexName, locale);
			return new ImportSession(
					indexName,
					initNewIndex(indexName, locale));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public abstract boolean indexExists(String indexName);

	/**
	 * <p>
	 * Checks if an active import session exists for that index.
	 * </p>
	 * <p>
	 * This could either be the full or minimal/final index name.
	 * </p>
	 * <p>
	 * The locale is not necessary, because you should never use the same
	 * index-name with different locales and expect two indexes to work in
	 * parallel.
	 * </p>
	 * 
	 * @param indexName
	 * @return
	 */
	public abstract boolean isImportRunning(String indexName);

	protected abstract String initNewIndex(String indexName, String locale) throws IOException;

	/**
	 * Get a map of all matching indexes that are not deployed yet (which means
	 * they are still considered as running.
	 * Each one with the according index creation time.
	 * 
	 * @param indexName
	 * @return
	 */
	public abstract Map<String, Instant> getRunningImportStartTimes(String indexName, String locale);

	private void cleanupAbandonedImports(String indexName, String locale) {
		Map<String, Instant> activeImportStartTime = getRunningImportStartTimes(indexName, locale);
		for (Entry<String, Instant> indexStartTimes : activeImportStartTime.entrySet()) {
			Duration activeImportAge = Duration.between(indexStartTimes.getValue(), Instant.now());
			if (activeImportAge.toHours() > 0) {
				log.info("Deleting index {} which was created {} ago",
						indexStartTimes.getKey(),
						DurationFormatUtils.formatDurationWords(activeImportAge.toMillis(), true, true));
				deleteIndex(indexStartTimes.getKey());
			}
		}
	}

	@Override
	public int add(BulkImportData data) throws Exception {
		validateSession(data.session);
		List<IndexableItem> bulk = new ArrayList<>();
		for (Document doc : data.getDocuments()) {
			// FIXME: document processors should work on indexable item
			// so they are able to modify only the usage dependent data, e.g.
			// only searchable data, instead also changing the result data!
			boolean isIndexable = preProcess(doc);
			if (isIndexable) bulk.add(indexItemConverter.toIndexableItem(doc));
		}
		log.info("converted {} of {} documents", bulk.size(), data.documents.length);
		if (bulk.size() > 0) {
			return addToIndex(data.getSession(), bulk);
		}
		else {
			return 0;
		}
	}

	protected abstract int addToIndex(ImportSession session, List<IndexableItem> bulk) throws Exception;

	private boolean preProcess(Document doc) {
		boolean isIndexable = true;

		combiFieldBuilder.build(doc);
		for (DocumentPreProcessor preProcessor : dataPreProcessors) {
			isIndexable = preProcessor.process(doc, isIndexable);
		}
		return isIndexable;
	}

	protected abstract void validateSession(ImportSession session) throws IllegalArgumentException;

	@Override
	public boolean done(ImportSession session) throws Exception {
		validateSession(session);
		return deploy(session);
	}

	protected abstract boolean deploy(ImportSession session);

	@Override
	public void cancel(ImportSession session) {
		validateSession(session);
		deleteIndex(session.temporaryIndexName);
	}

	protected abstract void deleteIndex(String indexName);

	protected abstract Document _get(@NonNull String indexName, @NonNull String docId);

	@Override
	public Map<String, Result> patchDocuments(String indexName, List<Document> documents) {
		Map<String, Result> response = new HashMap<>(documents.size());
		for (Document doc : documents) {
			response.put(doc.id, patchDocument(indexName, doc));
		}
		return response;
	}

	public Result patchDocument(String index, Document doc) {
		Set<String> fetchFields = DocumentPatcher.getRequiredFieldsForMerge(doc, fieldConfIndex);

		Document patchedDoc = doc;
		if (!fetchFields.isEmpty()) {
			// fetch the document from ES and patch it
			Document indexedDoc = _get(index, doc.getId());
			if (indexedDoc == null) return Result.NOT_FOUND;

			patchedDoc = DocumentPatcher.patchDocument(doc, indexedDoc, fieldConfIndex);

		}
		// XXX for some Preprocessor this might lead to unwanted results,
		// because we also have data from the index, which were already
		// preprocessed => Preprocessor should be idempotent!
		preProcess(patchedDoc);
		IndexableItem indexableDoc = indexItemConverter.toIndexableItem(patchedDoc);
		return _patch(index, indexableDoc);
	}

	protected abstract Result _patch(String index, IndexableItem indexableItem);

	@Override
	public Map<String, Result> putDocuments(String indexName, Boolean replaceExisting, List<Document> documents) {
		Map<String, Result> response = new HashMap<>(documents.size());
		for (Document doc : documents) {
			response.put(doc.id, putDocument(indexName, replaceExisting, doc));
		}
		return response;
	}

	public Result putDocument(String indexName, Boolean replaceExisting, Document doc) {
		boolean isIndexable = preProcess(doc);
		if (isIndexable) return _put(indexName, replaceExisting, indexItemConverter.toIndexableItem(doc));
		else return Result.NOOP;
	}

	protected abstract Result _put(String indexName, Boolean replaceExisting, IndexableItem indexableItem);

}
