package de.cxp.ocs.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.client.ImportClient;
import de.cxp.ocs.client.deserializer.ObjectMapperFactory;
import de.cxp.ocs.model.index.BulkImportData;
import de.cxp.ocs.model.index.Document;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DataIndexer {

	@NonNull
	private final ImportClient importClient;

	private final ObjectMapper mapper = ObjectMapperFactory.createObjectMapper();

	// for testing purposes use bulks of 3 documents, but these can
	// be many more, depending of the desired request size
	@Setter
	private int bulkSize = 3;

	@Setter
	private String langcode = "en";

	public int indexTestData(String indexName) throws Exception {
		return this.indexTestData(indexName, "testdata.jsonl");
	}

	public int indexTestData(String indexName, String resourceName) throws Exception {
		InputStream testDataStream = DataIndexer.class.getClassLoader().getResourceAsStream(resourceName);
		if (testDataStream == null) {
			throw new Exception("resource not found: " + resourceName);
		}

		try (final BufferedReader reader = new BufferedReader(new InputStreamReader(testDataStream))) {
			var resourceIterator = new Iterator<Document>() {

				String docSource;

				@Override
				public boolean hasNext() {
					try {
						return (docSource = reader.readLine()) != null;
					}
					catch (IOException e) {
						log.error("Failed to read from resource {}", resourceName, e);
						return false;
					}
				}

				@Override
				public Document next() {
					if (docSource == null) {
						throw new NoSuchElementException();
					}
					try {
						return mapper.readValue(docSource, Document.class);
					}
					catch (JsonProcessingException e) {
						log.error("Failed to deserialize document '{}'", docSource, e);
						if (hasNext()) return next();
						else return null;
					}
				}
			};

			return indexTestData(indexName, resourceIterator);
		}
	}

	/**
	 * index given data into specified index using bulk indexation.
	 * 
	 * @param indexName
	 *        name of the index to add
	 * @param documentProvider
	 *        documents
	 * @return the amount of indexed documents, or -1 if indexation failed or was rejected.
	 * @throws Exception
	 */
	public int indexTestData(String indexName, Iterator<Document> documentProvider) throws Exception {
		log.info("indexing data into index {}", indexName);

		// start a new import session for a new index to bulk-index data into it
		ImportSession importSession = importClient.startImport(indexName, langcode);

		int addedDocuments = 0;
		try {
			List<Document> bulkedDocs = new ArrayList<>();

			while (documentProvider.hasNext()) {
				// here we simply deserialize proper documents, but they can
				// also be assembled from other data sources
				bulkedDocs.add(documentProvider.next());

				if (bulkedDocs.size() == bulkSize) {
					addedDocuments += sendBulk(indexName, importSession, bulkedDocs);
					bulkedDocs.clear();
				}
			}

			if (!bulkedDocs.isEmpty()) {
				addedDocuments += sendBulk(indexName, importSession, bulkedDocs);
			}
		}
		catch (Exception e) {
			log.error("indexing into index {} failed", indexName, e);
			// make sure to cancel a import session upon error, otherwise a new
			// import won't be possible due to an active import session (at
			// least until some cleanup process removes opened import sessions)
			importClient.cancel(importSession);
			throw e;
		}

		// finalize the import session, which makes it available with the
		// desired index name (using ES alias functionality).
		log.info("indexing into {} done", indexName);
		return importClient.done(importSession) ? addedDocuments : -1;
	}

	private int sendBulk(String indexName, ImportSession importSession, List<Document> bulkedDocs) throws Exception {
		BulkImportData data = new BulkImportData();
		data.session = importSession;
		data.documents = bulkedDocs.toArray(new Document[0]);
		int addCount = importClient.add(data);
		log.info("added {} documents to index {}", addCount, indexName);
		return addCount;
	}

}
