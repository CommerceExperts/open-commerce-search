package de.cxp.ocs.client;

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
	public Result patchDocument(String indexName, Document doc) {
		return target.patchDocument(indexName, doc);
	}

	@Override
	public Result putDocument(String indexName, Boolean replaceExisting, Document doc) {
		return target.putDocument(indexName, replaceExisting == null ? true : replaceExisting, doc);
	}

	@Override
	public Result deleteDocument(String indexName, String id) {
		return target.deleteDocument(indexName, id);
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
