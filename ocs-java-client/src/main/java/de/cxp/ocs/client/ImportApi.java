package de.cxp.ocs.client;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.model.index.BulkImportData;
import de.cxp.ocs.model.index.Document;
import feign.Param;
import feign.RequestLine;

interface ImportApi {

	@RequestLine("GET /indexer-api/v1/full/start/{indexName}?locale={locale}")
	ImportSession startImport(@Param("indexName") String indexName, @Param("locale") String locale);

	@RequestLine("POST /indexer-api/v1/full/add")
	int add(BulkImportData data) throws Exception;

	@RequestLine("POST /indexer-api/v1/full/done")
	boolean done(ImportSession session) throws Exception;

	@RequestLine("POST /indexer-api/v1/full/cancel")
	void cancel(ImportSession session);

	@RequestLine("PATCH /indexer-api/v1/update/{indexName}")
	void patchDocument(@Param("indexName") String indexName, Document doc);

	@RequestLine("PUT /indexer-api/v1/update/{indexName}?replaceExisting={replaceExisting}")
	void putDocument(@Param("indexName") String indexName, @Param("replaceExisting") Boolean replaceExisting, Document doc);

	@RequestLine("DELETE /indexer-api/v1/update/{indexName}?id=id")
	void deleteDocument(@Param("indexName") String indexName, String id);
}
