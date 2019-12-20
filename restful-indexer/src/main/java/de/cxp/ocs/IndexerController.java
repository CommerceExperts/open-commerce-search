package de.cxp.ocs;

import java.io.IOException;
import java.util.Locale;
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

import de.cxp.ocs.api.indexer.FullIndexer;
import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.conf.ApplicationProperties;
import de.cxp.ocs.conf.IndexConfiguration;
import de.cxp.ocs.model.index.Document;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(path = "/indexer/v1")
@Slf4j
public class IndexerController implements FullIndexer {

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
	@Override
	public ImportSession startImport(@PathVariable("indexName") String indexName, @RequestParam("locale") Locale locale)
			throws IllegalStateException {
		try {
			return actualIndexers.get(indexName).startImport(indexName, locale);
		}
		catch (ExecutionException e) {
			throw new RuntimeException(e);
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
	@PostMapping("/add/{indexName}")
	public void addProducts(
			@PathVariable("indexName") String indexName,
			@RequestParam("tempIndexName") String temporaryIndexName,
			@RequestBody Document[] documents) throws Exception {
		addProducts(new ImportSession(indexName, temporaryIndexName), documents);
	}

	@Override
	public void addProducts(ImportSession session, Document[] documents) throws Exception {
		actualIndexers.get(session.getFinalIndexName())
				.addProducts(session, documents);
	}

	@PostMapping("/done")
	@Override
	public boolean done(@RequestBody ImportSession session) throws Exception {
		return actualIndexers.get(session.getFinalIndexName())
				.done(session);
	}

	@PostMapping("/cancel")
	@Override
	public boolean cancel(@RequestBody ImportSession session) {
		try {
			return actualIndexers.get(session.getFinalIndexName())
					.cancel(session);
		}
		catch (ExecutionException e) {
			log.error("exception while canceling import: ", e);
			return false;
		}
	}

}
