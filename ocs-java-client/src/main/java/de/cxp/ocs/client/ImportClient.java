package de.cxp.ocs.client;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import de.cxp.ocs.api.indexer.FullIndexationService;
import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.api.indexer.UpdateIndexService;
import de.cxp.ocs.client.deserializer.ObjectMapperFactory;
import de.cxp.ocs.model.index.BulkImportData;
import de.cxp.ocs.model.index.Document;
import feign.Feign;
import feign.Feign.Builder;
import feign.codec.Decoder;
import feign.httpclient.ApacheHttpClient;

public class ImportClient implements FullIndexationService, UpdateIndexService {

	private ImportApi target;

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

	@Override
	public Map<String, Result> patchDocuments(String indexName, List<Document> docs) {
		return target.patchDocuments(indexName, docs);
	}

	@Override
	public Map<String, Result> putDocuments(String indexName, Boolean replaceExisting, List<Document> docs) {
		return target.putDocuments(indexName, replaceExisting == null ? true : replaceExisting, docs);
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
