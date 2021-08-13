package de.cxp.ocs.client;

import java.util.List;
import java.util.Map;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.api.indexer.UpdateIndexService.Result;
import de.cxp.ocs.model.index.BulkImportData;
import de.cxp.ocs.model.index.Document;
import feign.Headers;
import feign.Param;
import feign.RequestLine;

interface ImportApi {

	@RequestLine("GET /indexer-api/v1/full/start/{indexName}?locale={locale}")
	ImportSession startImport(@Param("indexName") String indexName, @Param("locale") String locale);

	@RequestLine("POST /indexer-api/v1/full/add")
	@Headers("Content-Type: application/json")
	int add(BulkImportData data) throws Exception;

	@RequestLine("POST /indexer-api/v1/full/done")
	@Headers("Content-Type: application/json")
	boolean done(ImportSession session) throws Exception;

	@RequestLine("POST /indexer-api/v1/full/cancel")
	@Headers("Content-Type: application/json")
	void cancel(ImportSession session);

	@RequestLine("PATCH /indexer-api/v1/update/{indexName}")
	@Headers("Content-Type: application/json")
	Map<String, Result> patchDocuments(@Param("indexName") String indexName, List<Document> doc);

	@RequestLine("PUT /indexer-api/v1/update/{indexName}?replaceExisting={replaceExisting}")
	@Headers("Content-Type: application/json")
	Map<String, Result> putDocuments(@Param("indexName") String indexName, @Param("replaceExisting") Boolean replaceExisting, List<Document> doc);

	@RequestLine("DELETE /indexer-api/v1/update/{indexName}")
	Map<String, Result> deleteDocuments(@Param("indexName") String indexName, @Param("id") List<String> id);
}
