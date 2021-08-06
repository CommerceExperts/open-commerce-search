package de.cxp.ocs.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.cxp.ocs.api.indexer.UpdateIndexService;
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
	public Map<String, Result> patchDocuments(@PathVariable("indexName") String indexName, @RequestBody List<Document> documents) {
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
	}

	@PutMapping
	@Override
	public Map<String, Result> putDocuments(
			@PathVariable("indexName") String indexName,
			@RequestParam(name = "replaceExisting", defaultValue = "true") Boolean replaceExisting,
			@RequestBody List<Document> documents) {
		try {
			return indexerManager.getIndexer(indexName)
					.putDocuments(indexName, replaceExisting, documents);
		}
		catch (ExecutionException e) {
			log.error("failed to get indexer", e);
			throw new RuntimeException(e);
		}
	}

	@DeleteMapping
	@Override
	public Map<String, Result> deleteDocuments(@PathVariable("indexName") String indexName, @RequestParam("id[]") List<String> ids) {
		try {
			return indexerManager.getIndexer(indexName)
					.deleteDocuments(indexName, ids);
		}
		catch (ExecutionException e) {
			log.error("failed to get indexer", e);
			throw new RuntimeException(e);
		}
	}

}
