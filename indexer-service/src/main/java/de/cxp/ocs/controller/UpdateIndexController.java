package de.cxp.ocs.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.api.indexer.UpdateIndexService;
import de.cxp.ocs.indexer.AbstractIndexer;
import de.cxp.ocs.model.index.Document;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(path = "/indexer-api/v1/update/{indexName}")
public class UpdateIndexController implements UpdateIndexService {

	@Autowired
	private IndexerCache indexerManager;

	@PatchMapping
	@Override
	public Map<String, Result> patchDocuments(@PathVariable("indexName")
	String indexName, @RequestBody
	List<Document> documents) {
		MDC.put("index", indexName);
		try {
			Map<String, Result> response = new HashMap<>(documents.size());
			for (Document doc : documents) {
				response.put(doc.id, indexerManager.getIndexer(indexName)
						.patchDocument(indexName, doc));
			}
			return response;
		}
		catch (ExecutionException e) {
			log.error("failed to get indexer", e);
			throw new RuntimeException(e);
		}
		finally {
			MDC.remove("index");
		}
	}

	@PutMapping
	@Override
	public Map<String, Result> putDocuments(
			@PathVariable("indexName")
			String indexName,
			@RequestParam(name = "replaceExisting", defaultValue = "true")
			Boolean replaceExisting,
			@RequestBody
			List<Document> documents) {
		MDC.put("index", indexName);
		try {
			AbstractIndexer indexer = indexerManager.getIndexer(indexName);
			if (!indexer.indexExists(indexName)) {
				return putIntoNewIndex(indexer, indexName, replaceExisting, documents);
			}
			else {
				return indexer.putDocuments(indexName, replaceExisting, documents);
			}
		}
		catch (IllegalArgumentException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("failed to get indexer", e);
			throw new RuntimeException(e);
		}
		finally {
			MDC.remove("index");
		}
	}

	private Map<String, Result> putIntoNewIndex(AbstractIndexer indexer, String indexName, Boolean replaceExisting, List<Document> documents) throws Exception {
		if (indexName.lastIndexOf('-') == -1) {
			throw new IllegalArgumentException("Index " + indexName + " does not exist and not in require format <name>-<locale> to create a new one automatically.");
		}

		String locale = indexName.substring(indexName.lastIndexOf('-') + 1);
		ImportSession importSession = indexer.startImport(indexName, locale);
		Map<String, Result> putResult;
		try {
			putResult = indexer.putDocuments(importSession.getTemporaryIndexName(), replaceExisting, documents);
			indexer.done(importSession);
		}
		catch (Exception e) {
			indexer.cancel(importSession);
			throw e;
		}
		return putResult;
	}

	@DeleteMapping
	@Override
	public Map<String, Result> deleteDocuments(@PathVariable("indexName")
	String indexName, @RequestParam("id")
	List<String> ids) {
		MDC.put("index", indexName);
		try {
			return indexerManager.getIndexer(indexName)
					.deleteDocuments(indexName, ids);
		}
		catch (ExecutionException e) {
			log.error("failed to get indexer", e);
			throw new RuntimeException(e);
		}
		finally {
			MDC.remove("index");
		}
	}

}
