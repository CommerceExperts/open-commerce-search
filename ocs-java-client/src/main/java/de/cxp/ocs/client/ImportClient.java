package de.cxp.ocs.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import de.cxp.ocs.api.indexer.FullIndexationService;
import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.api.indexer.UpdateIndexService;
import de.cxp.ocs.client.deserializer.ObjectMapperFactory;
import de.cxp.ocs.model.index.BulkImportData;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import feign.Feign;
import feign.Feign.Builder;
import feign.codec.Decoder;
import feign.httpclient.ApacheHttpClient;

public class ImportClient implements FullIndexationService, UpdateIndexService {

	private final ImportApi target;

	/**
	 * With this constructor the Feign::Builder can be configured.
	 * 
	 * @param endpointUrl
	 * @param feignConfigurer
	 */
	public ImportClient(String endpointUrl, Consumer<Feign.Builder> feignConfigurer) {
		Builder fb = Feign.builder();
		feignConfigurer.accept(fb);
		fb.client(new ApacheHttpClient());
		target = fb.target(ImportApi.class, endpointUrl);
	}

	/**
	 * Initializes the SearchClient with the given endpointUrl and the default
	 * Jackson encoder.
	 * If this constructor is not used, Jackson is not necessary on the
	 * classpath. Instead take care of a working {@link Decoder}.
	 * 
	 * @param endpointUrl
	 */
	public ImportClient(String endpointUrl) {
		this(endpointUrl, f -> {
			f.encoder(ObjectMapperFactory.createJacksonEncoder());
			f.decoder(ObjectMapperFactory.createJacksonDecoder());
			return;
		});
	}

	/**
	 * Patch one or more documents. The passed documents only need partial data
	 * that needs to be patched and the ID of the documents to patch.
	 * 
	 * Attention: in order to patch Products with variants, use the
	 * "patchProducts" method, which is necessary to have them serialized
	 * properly.
	 */
	@Override
	public Map<String, Result> patchDocuments(String indexName, List<Document> docs) {
		if (docs.stream().anyMatch(d -> d instanceof Product)) {
			DocumentBulkSplit docsSplit = new DocumentBulkSplit(docs);

			Map<String, Result> results = new HashMap<>();
			if (!docsSplit.products.isEmpty()) {
				results.putAll(target.patchProducts(indexName, docsSplit.products));
			}
			if (!docsSplit.documents.isEmpty()) {
				results.putAll(target.patchDocuments(indexName, docsSplit.documents));
			}
			return results;
		}
		else {
			return target.patchDocuments(indexName, docs);
		}
	}

	/**
	 * Similar to patchDocuments, but for the extended sub type {@link Product}
	 * that supports variants. For some reason this is necessary.
	 * 
	 * XXX: may be solved with custom serializer.
	 * 
	 * @param indexName
	 * @param products
	 * @return
	 */
	public Map<String, Result> patchProducts(String indexName, List<Product> products) {
		return target.patchProducts(indexName, products);
	}

	/**
	 * Add or overwrite existing documents.
	 * 
	 * Attention: in order to put Products with variants, use the
	 * "putProducts" method, which is necessary to have them serialized
	 * properly.
	 */
	@Override
	public Map<String, Result> putDocuments(String indexName, Boolean replaceExisting, String langCode, List<Document> docs) {
		if (docs.stream().anyMatch(d -> d instanceof Product)) {
			// work around the serialization problem of feign that causes products to be not indexed with variants if
			// injected via putDocuments.
			DocumentBulkSplit docsSplit = new DocumentBulkSplit(docs);

			Map<String, Result> results = new HashMap<>();
			if (!docsSplit.products.isEmpty()) {
				results.putAll(target.putProducts(indexName, replaceExisting == null || replaceExisting, langCode, docsSplit.products));
			}
			if (!docsSplit.documents.isEmpty()) {
				results.putAll(target.putDocuments(indexName, replaceExisting == null || replaceExisting, langCode, docsSplit.documents));
			}
			return results;
		}
		else {
			return target.putDocuments(indexName, replaceExisting == null || replaceExisting, langCode, docs);
		}
	}

	
	/**
	 * Similar to putDocuments, but for the extended sub type {@link Product}
	 * that supports variants.
	 * @param indexName
	 * @param replaceExisting
	 * @param products
	 * @return
	 */
	public Map<String, Result> putProducts(String indexName, Boolean replaceExisting, String langCode, List<Product> products) {
		return target.putProducts(indexName, replaceExisting == null || replaceExisting, langCode, products);
	}

	@Override
	public Map<String, Result> deleteDocuments(String indexName, List<String> ids) {
		return target.deleteDocuments(indexName, ids);
	}

	@Override
	public ImportSession startImport(String indexName, String locale) throws IllegalStateException {
		return target.startImport(indexName, locale);
	}

	@Override
	public int add(BulkImportData data) throws Exception {
		return target.add(data);
	}

	@Override
	public boolean done(ImportSession session) throws Exception {
		return target.done(session);
	}

	@Override
	public void cancel(ImportSession session) {
		target.cancel(session);
	}

}
