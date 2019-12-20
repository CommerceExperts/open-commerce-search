package de.cxp.ocs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;

import de.cxp.ocs.api.indexer.FullIndexer;
import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.preprocessor.CombiFieldBuilder;
import de.cxp.ocs.preprocessor.DataPreProcessor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor = @__({ @Autowired }))
public abstract class AbstractIndexer implements FullIndexer {

	@NonNull
	final List<DataPreProcessor> dataPreProcessors;
	
	@NonNull
	final CombiFieldBuilder combiFieldBuilder;

	@Override
	public ImportSession startImport(String indexName, Locale locale) throws IllegalStateException {
		if (isImportRunning(indexName)) {
			throw new IllegalStateException("Import for index " + indexName + " already running");
		}
		return new ImportSession(
				indexName,
				initNewIndex(indexName, locale));
	}

	protected abstract boolean isImportRunning(String indexName);

	protected abstract String initNewIndex(String indexName, Locale locale);

	@Override
	public void addProducts(ImportSession session, Document[] documents) throws Exception {
		List<Document> bulk = new ArrayList<>();
		for (Document doc : documents) {
			boolean isIndexable = preProcess(doc);
			if (isIndexable) bulk.add(doc);
		}
		if (bulk.size() > 0) {
			addToIndex(session, bulk);
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
