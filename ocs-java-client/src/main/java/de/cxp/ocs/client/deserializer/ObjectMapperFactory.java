package de.cxp.ocs.client.deserializer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import feign.codec.Decoder;
import feign.jackson.JacksonDecoder;

public class ObjectMapperFactory {

	public static Decoder createJacksonDecoder() {
		return new JacksonDecoder(createObjectMapper());
	}

	public static ObjectMapper createObjectMapper() {
		final ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

		objectMapper.registerModule(new ParameterNamesModule(Mode.PROPERTIES));
		objectMapper.addMixIn(Facet.class, FacetMixin.class);
		objectMapper.addMixIn(SearchQuery.class, SearchQueryCreator.class);

		SimpleModule deserializerModule = new SimpleModule();
		deserializerModule.addDeserializer(Document.class, new DocumentDeserializer());
		deserializerModule.addDeserializer(Product.class, new ProductDeserializer());
		deserializerModule.addDeserializer(FacetEntry.class, new FacetEntryDeserializer());
		objectMapper.registerModule(deserializerModule);
		return objectMapper;
	}

	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "_type")
	public static abstract class WithTypeInfo {}

	public static abstract class SearchQueryCreator {

		@JsonCreator
		SearchQueryCreator(String label) {}
	}

	public static abstract class AttributeCreator {

		@JsonCreator
		AttributeCreator(String id, String label, String code, String value) {}
	}

	public static abstract class FacetMixin {

		@JsonCreator
		FacetMixin(String name) {}

		@JsonIgnore
		abstract String getLabel();

	}

}
