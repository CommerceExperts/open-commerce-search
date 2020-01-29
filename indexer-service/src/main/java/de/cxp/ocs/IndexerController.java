package de.cxp.ocs;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.conf.ApplicationProperties;
import de.cxp.ocs.conf.IndexConfiguration;
import de.cxp.ocs.indexer.AbstractIndexer;
import de.cxp.ocs.indexer.IndexerFactory;
import de.cxp.ocs.model.index.BulkImportData;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(path = "/indexer-api/v1/full")
@Slf4j
/**
 * Controller that implements the FullIndexationService
 * 
 * TODO: implement UpdateIndexService
 */
// unfortunately it's not possible to use 'implements FullIndexationService'
// properly, because the http-code statuses need ResponseEntity as return type
// in Spring Boot.
public class IndexerController {

	@Autowired
	private IndexerFactory indexerFactory;

	@Autowired
	private ApplicationProperties properties;

	private LoadingCache<String, AbstractIndexer> actualIndexers = CacheBuilder.newBuilder()
			.expireAfterAccess(15, TimeUnit.MINUTES)
			.build(new CacheLoader<String, AbstractIndexer>() {

				@Override
				public AbstractIndexer load(String indexName) throws Exception {
					IndexConfiguration indexConfiguration = properties.getIndexConfig().get(indexName);
					if (indexConfiguration == null) {
						indexConfiguration = properties.getDefaultIndexConfig();
					}
					return indexerFactory.create(indexConfiguration);
				}
			});

	@ExceptionHandler(
			value = { ExecutionException.class, IOException.class, RuntimeException.class,
					ClassNotFoundException.class })
	public ResponseEntity<String> handleInternalErrors(Exception e) {
		final String errorId = UUID.randomUUID().toString();
		log.error("Internal Server Error " + errorId, e);
		return new ResponseEntity<>("Something went wrong. Error reference: " + errorId,
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@GetMapping("/start/{indexName}")
	public ResponseEntity<ImportSession> startImport(@PathVariable("indexName") String indexName, @RequestParam("locale") String locale) {
		if (indexName == null || indexName.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		if (locale == null || locale.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		try {
			return ResponseEntity.ok(actualIndexers.get(indexName).startImport(indexName, locale));
		}
		catch (IllegalStateException ise) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		catch (ExecutionException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Add products to import into current session.
	 * 
	 * @param indexName
	 * @param temporaryIndexName
	 *        that should be retrieved from ImportSession that was created at
	 *        the start of the import
	 * @param documents
	 * @throws Exception
	 */
	@PostMapping("/add")
	public ResponseEntity<String> add(@RequestBody BulkImportData data) throws Exception {
		if (data.session == null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("import-session missing in bulk");
		if (data.documents == null || data.documents.length == 0) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("no documents provided in bulk");
		}

		AbstractIndexer indexer = actualIndexers.get(data.getSession().getFinalIndexName());
		if (!indexer.isImportRunning(data.session.temporaryIndexName)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}
		indexer.add(data);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/done")
	public ResponseEntity<Boolean> done(@RequestBody ImportSession session) throws Exception {
		AbstractIndexer indexer = actualIndexers.get(session.getFinalIndexName());
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
			AbstractIndexer indexer = actualIndexers.get(session.getFinalIndexName());
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
