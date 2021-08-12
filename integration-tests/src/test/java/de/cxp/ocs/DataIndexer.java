package de.cxp.ocs;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.client.ImportClient;
import de.cxp.ocs.client.deserializer.ObjectMapperFactory;
import de.cxp.ocs.model.index.BulkImportData;
import de.cxp.ocs.model.index.Document;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DataIndexer {

	@NonNull
	private final ImportClient importClient;

	private final ObjectMapper mapper = ObjectMapperFactory.createObjectMapper();

	public boolean indexTestData(String indexName) throws Exception {
		log.info("indexing data into index {}", indexName);
		InputStream testDataStream = DataIndexer.class.getClassLoader().getResourceAsStream("testdata.jsonl");
		assert testDataStream != null;

		// start a new import session for a new index to bulk-index data into it
		ImportSession importSession = importClient.startImport(indexName, "en");

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(testDataStream))) {
			List<Document> bulkedDocs = new ArrayList<>();
			String docSource;
			while ((docSource = reader.readLine()) != null) {
				// here we simply deserialize proper documents, but they can
				// also be assembled from other data sources
				bulkedDocs.add(mapper.readValue(docSource, Document.class));

				// for testing purposes use bulks of 3 documents, but these can
				// be many more, depending of the desired request size
				if (bulkedDocs.size() == 3) {
					sendBulk(indexName, importSession, bulkedDocs);
					bulkedDocs.clear();
				}
			}

			if (bulkedDocs.size() > 0) {
				sendBulk(indexName, importSession, bulkedDocs);
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
		return importClient.done(importSession);
	}

	private void sendBulk(String indexName, ImportSession importSession, List<Document> bulkedDocs) throws Exception {
		BulkImportData data = new BulkImportData();
		data.session = importSession;
		data.documents = bulkedDocs.toArray(new Document[0]);
		log.info("added {} documents to index {}", importClient.add(data), indexName);
	}

}
