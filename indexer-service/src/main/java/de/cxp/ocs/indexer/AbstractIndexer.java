package de.cxp.ocs.indexer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.LocaleUtils;

import de.cxp.ocs.api.indexer.FullIndexationService;
import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.api.indexer.UpdateIndexService;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.model.index.BulkImportData;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.spi.indexer.DocumentPostProcessor;
import de.cxp.ocs.spi.indexer.DocumentPreProcessor;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractIndexer implements FullIndexationService, UpdateIndexService {

	@NonNull
	private final List<DocumentPreProcessor> dataPreProcessors;

	@Getter(value = AccessLevel.PROTECTED)
	@NonNull
	final FieldConfigIndex fieldConfIndex;

	private final IndexItemConverter indexItemConverter;

	/**
	 * This property defines how old should an index be to be deleted if it still is not assigned to an alias.
	 * 
	 * There is also the scheduled AbandonedIndexCleanupTask that takes care for any abandoned index.
	 * Since that scheduled task won't consider newly started index runs, it has a higher default
	 * deletion threshold age. That's why we won't use that age setting (injected via property) here.
	 */
	@Setter
	private int abandonedIndexDeletionAgeSeconds = 60 * 60; // 1h default

	public AbstractIndexer(
			@NonNull List<DocumentPreProcessor> dataPreProcessors,
			@NonNull List<DocumentPostProcessor> postProcessors,
			@NonNull FieldConfigIndex fieldConfIndex) {
		this.dataPreProcessors = dataPreProcessors;
		this.fieldConfIndex = fieldConfIndex;
		indexItemConverter = new IndexItemConverter(fieldConfIndex, postProcessors);
	}

	@Override
	public ImportSession startImport(String indexName, String locale) throws IllegalStateException {
		if (!indexName.equals(indexName.toLowerCase(LocaleUtils.toLocale(locale)))) {
			throw new IllegalArgumentException(String.format("Invalid index name [%s], must be lowercase", indexName));
		}
		if (isImportRunning(indexName, locale)) {
			log.warn("Another import for index {} is already running! Will start a new one never the less...", indexName);
			CompletableFuture.runAsync(() -> this.cleanupAbandonedImports(indexName, abandonedIndexDeletionAgeSeconds));
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

	protected abstract void cleanupAbandonedImports(String indexName, int minAgeSeconds);

	public abstract boolean indexExists(String indexName, String locale);

	/**
	 * <p>
	 * Checks if an active import session exists for that index.
	 * </p>
	 * <p>
	 * This could either be the full or minimal/final index name.
	 * </p>
	 * <p>
	 * 
	 * @param indexName
	 * @param locale
	 * @return
	 */
	public abstract boolean isImportRunning(String indexName, String locale);

	protected abstract String initNewIndex(String indexName, String locale) throws IOException;

	@Override
	public int add(BulkImportData data) throws Exception {
		validateSession(data.session);
		List<IndexableItem> bulk = new ArrayList<>();
		for (Document doc : data.getDocuments()) {
			try {
				boolean isIndexable = preProcess(doc);
				if (isIndexable) bulk.add(indexItemConverter.toIndexableItem(doc));
			}
			catch (Exception x) {
				log.info("Dismissed added document {} due to {}: {}", doc.getId(), x.getClass().getCanonicalName(), x.getMessage());
			}
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

		IndexableItem indexableDoc;
		try {
			// XXX for some Preprocessor this might lead to unwanted results,
			// because we also have data from the index, which were already
			// preprocessed => Preprocessor should be idempotent!
			preProcess(patchedDoc);
			indexableDoc = indexItemConverter.toIndexableItem(patchedDoc);
		}
		catch (Exception x) {
			log.info("Dismissed patched document {} due to {}: {}", doc.getId(), x.getClass().getCanonicalName(), x.getMessage());
			return Result.DISMISSED;
		}

		return _patch(index, indexableDoc);
	}

	protected abstract Result _patch(String index, IndexableItem indexableItem);

	@Override
	public Map<String, Result> putDocuments(String indexName, Boolean replaceExisting, String langCode, List<Document> documents) {
		return putDocuments(indexName, replaceExisting, documents);
	}

	public Map<String, Result> putDocuments(String indexName, Boolean replaceExisting, List<Document> documents) {
		Map<String, Result> response = new HashMap<>(documents.size());
		for (Document doc : documents) {
			response.put(doc.id, putDocument(indexName, replaceExisting, doc));
		}
		return response;
	}

	public Result putDocument(String indexName, Boolean replaceExisting, Document doc) {
		Result result = Result.NOOP;
		try {
			boolean isIndexable = preProcess(doc);
			if (isIndexable) {
				result = _put(indexName, replaceExisting, indexItemConverter.toIndexableItem(doc));
			}
		}
		catch (Exception x) {
			log.info("Dismissed update of document {} due to {}: {}", doc.getId(), x.getClass().getCanonicalName(), x.getMessage());
			result = Result.DISMISSED;
		}
		return result;
	}

	protected abstract Result _put(String indexName, Boolean replaceExisting, IndexableItem indexableItem);

}
