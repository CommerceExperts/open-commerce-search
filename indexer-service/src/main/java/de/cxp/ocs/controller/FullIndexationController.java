package de.cxp.ocs.controller;

import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.indexer.AbstractIndexer;
import de.cxp.ocs.model.index.BulkImportData;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(path = "/indexer-api/v1/full")
@Slf4j
/**
 * Controller that implements the FullIndexationService
 */
// unfortunately it's not possible to use 'implements FullIndexationService'
// properly, because the http-code statuses need ResponseEntity as return type
// in Spring Boot.
public class FullIndexationController {

	@Autowired
	private IndexerCache indexerManager;

	@GetMapping("/start/{indexName}")
	public ResponseEntity startImport(@PathVariable("indexName") String indexName, @RequestParam("locale") String locale) {
		if (indexName == null || indexName.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		if (locale == null || locale.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		try {
			return ResponseEntity.ok(indexerManager.getIndexer(indexName).startImport(indexName, locale));
		}
		catch (IllegalArgumentException argEx) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(argEx.getMessage());
		}
		catch (IllegalStateException ise) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(ise.getMessage());
		}
		catch (ExecutionException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Add products to import into current session.
	 * 
	 * @param data
	 *        {@link BulkImportData} that contains the ImportSession that was
	 *        created at the start of the import plus one or more documents to
	 *        be indexed
	 * @throws Exception
	 */
	@PostMapping("/add")
	public ResponseEntity<Integer> add(@RequestBody BulkImportData data) throws Exception {
		if (data.session == null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(0);
		if (data.documents == null || data.documents.length == 0) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(0);
		}

		AbstractIndexer indexer = indexerManager.getIndexer(data.getSession().getFinalIndexName());
		if (!indexer.isImportRunning(data.session.temporaryIndexName)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(0);
		}
		int successCount = indexer.add(data);
		return ResponseEntity.ok().body(successCount);
	}

	@PostMapping("/done")
	public ResponseEntity<Boolean> done(@RequestBody ImportSession session) throws Exception {
		AbstractIndexer indexer = indexerManager.getIndexer(session.getFinalIndexName());
		if (!indexer.isImportRunning(session.temporaryIndexName)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}
		try {
		boolean ok = indexer.done(session);
			return ok ? ResponseEntity.ok(true) : ResponseEntity.status(HttpStatus.BAD_REQUEST).body(false);
		}
		catch (IllegalArgumentException iae) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(false);
		}
	}

	@PostMapping("/cancel")
	public ResponseEntity<Void> cancel(@RequestBody ImportSession session) {
		try {
			AbstractIndexer indexer = indexerManager.getIndexer(session.getFinalIndexName());
			if (!indexer.isImportRunning(session.temporaryIndexName)) {
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
			}
			indexer.cancel(session);
			return ResponseEntity.accepted().build();
		}
		catch (ExecutionException e) {
			log.error("exception while canceling import: ", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

}
