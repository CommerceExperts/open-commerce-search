package de.cxp.ocs.client;

import java.util.List;
import java.util.Map;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.api.indexer.UpdateIndexService.Result;
import de.cxp.ocs.model.index.BulkImportData;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
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

	/**
	 * Patch documents that do not have variants into the specified search index. If documents with variants should be
	 * indexed (="Products"), then please use patchProducts instead.
	 * 
	 * @param indexName
	 * @param doc
	 * @return
	 */
	@RequestLine("PATCH /indexer-api/v1/update/{indexName}")
	@Headers("Content-Type: application/json")
	Map<String, Result> patchDocuments(@Param("indexName") String indexName, List<Document> doc);

	/**
	 * Alternative for patchDocuments that works around serialization problems of generic parameters. Should always be
	 * used if products (=documents with variants) are patched.
	 * 
	 * @param indexName
	 * @param doc
	 * @return
	 */
	@RequestLine("PATCH /indexer-api/v1/update/{indexName}")
	@Headers("Content-Type: application/json")
	Map<String, Result> patchProducts(@Param("indexName") String indexName, List<Product> doc);

	/**
	 * Put documents that do not have variants into the specified search index. If documents with variants should be
	 * indexed (="Products"), then please use putProducts instead.
	 * 
	 * @param indexName
	 * @param replaceExisting
	 * @param langCode
	 * @param docs
	 * @return
	 */
	@RequestLine("PUT /indexer-api/v1/update/{indexName}?replaceExisting={replaceExisting}&langCode={langCode}")
	@Headers("Content-Type: application/json")
	Map<String, Result> putDocuments(@Param("indexName") String indexName, @Param("replaceExisting") Boolean replaceExisting, @Param("langCode") String langCode, List<Document> docs);

	/**
	 * Alternative for putDocuments that works around serialization problems of generic parameters. Should always be
	 * used if products (=documents with variants) are added.
	 * 
	 * @param indexName
	 * @param replaceExisting
	 * @param langCode
	 * @param prod
	 * @return
	 */
	@RequestLine("PUT /indexer-api/v1/update/{indexName}?replaceExisting={replaceExisting}&langCode={langCode}")
	@Headers("Content-Type: application/json")
	Map<String, Result> putProducts(@Param("indexName") String indexName, @Param("replaceExisting") Boolean replaceExisting,  @Param("langCode") String langCode, List<Product> prod);

	@RequestLine("DELETE /indexer-api/v1/update/{indexName}?id={id}")
	Map<String, Result> deleteDocuments(@Param("indexName") String indexName, @Param("id") List<String> id);
}
