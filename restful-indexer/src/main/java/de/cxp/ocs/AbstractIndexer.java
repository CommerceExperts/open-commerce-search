package de.cxp.ocs;

import java.util.ArrayList;
import java.util.List;

import de.cxp.ocs.api.indexer.FullIndexationService;
import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.model.index.BulkImportData;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.preprocessor.CombiFieldBuilder;
import de.cxp.ocs.preprocessor.DataPreProcessor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractIndexer implements FullIndexationService {

	@NonNull
	final List<DataPreProcessor> dataPreProcessors;
	
	@NonNull
	final CombiFieldBuilder combiFieldBuilder;

	@Override
	public ImportSession startImport(String indexName, String locale) throws IllegalStateException {
		if (isImportRunning(indexName)) {
			throw new IllegalStateException("Import for index " + indexName + " already running");
		}
		return new ImportSession(
				indexName,
				initNewIndex(indexName, locale));
	}

	protected abstract boolean isImportRunning(String indexName);

	protected abstract String initNewIndex(String indexName, String locale);

	@Override
	public void add(BulkImportData data) throws Exception {
		List<Document> bulk = new ArrayList<>();
		for (Document doc : data.getDocuments()) {
			boolean isIndexable = preProcess(doc);
			if (isIndexable) bulk.add(doc);
		}
		if (bulk.size() > 0) {
			addToIndex(data.getSession(), bulk);
		}
	}

	protected abstract void addToIndex(ImportSession session, List<Document> bulk) throws Exception;

	private boolean preProcess(Document doc) {
		boolean isIndexable = true;
		combiFieldBuilder.build(doc);
		for (DataPreProcessor preProcessor : dataPreProcessors) {
			isIndexable = preProcessor.process(doc, isIndexable);
		}
		return isIndexable;
	}

}
