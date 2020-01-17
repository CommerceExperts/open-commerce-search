package de.cxp.ocs.model;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.HierarchialFacetEntry;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;
import de.cxp.ocs.model.result.SortOption;
import de.cxp.ocs.model.result.SortOrder;

public class SerializationTest {

	// we don't have control over serialization, so we just can assume standard
	// serialization
	final ObjectMapper serializer = new ObjectMapper();

	final ObjectMapper deserializer = new ObjectMapper();

	/**
	 * special configuration for jackson object mapper to work with the
	 * defined models.
	 */
	@BeforeEach
	public void configureDeserialization() {
		deserializer.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

		deserializer.registerModule(new ParameterNamesModule(Mode.PROPERTIES));
		deserializer.addMixIn(Facet.class, FacetMixin.class);
		deserializer.addMixIn(Attribute.class, DoubleStringArgsCreator.class);
		deserializer.addMixIn(SearchQuery.class, SingleStringArgsCreator.class);

		SimpleModule deserializerModule = new SimpleModule();
		deserializerModule.addDeserializer(Document.class, new DocumentDeserializer());
		deserializerModule.addDeserializer(Product.class, new ProductDeserializer());
		deserializerModule.addDeserializer(FacetEntry.class, new FacetEntryDeserializer());
		deserializer.registerModule(deserializerModule);
	}

	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "_type")
	public static abstract class WithTypeInfo {}

	public static abstract class SingleStringArgsCreator {

		@JsonCreator
		SingleStringArgsCreator(String name) {}
	}

	public static abstract class DoubleStringArgsCreator {

		@JsonCreator
		DoubleStringArgsCreator(String name, String id) {}
	}

	public static abstract class FacetMixin {

		@JsonCreator
		FacetMixin(String name) {}

		@JsonIgnore
		abstract String getLabel();

		@JsonIgnore
		abstract String getType();
	}

	@ParameterizedTest
	@MethodSource("getSerializableObjects")
	public void testRoundTrip(Object serializable) throws IOException {
		String serialized = serializer.writeValueAsString(serializable);
		System.out.println(serialized);
		Object deserialized = deserializer.readValue(serialized, serializable.getClass());

		if (serializable instanceof Document) {
			assertEqualDocuments((Document) serializable, (Document) deserialized, "");
		}
		else if (serializable instanceof Product) {
			assertEqualDocuments((Product) serializable, (Product) deserialized, "");
		}
		else {
			assertEquals(serializable, deserialized);
		}
	}

	public static Stream<Object> getSerializableObjects() {
		return Stream.of(

				new Product("1")
						.set("title", "string values test"),

				new Product("2")
						.set("title", "number values test")
						.set("number", 12.5),

				new Product("3")
						.set("title", "attribute with id test")
						.set("color", Attribute.of("Fruit", "12")),

				new Product("4.1")
						.set("title", "number array test")
						.set("sizes", 38, 39, 40, 41, 42, 42),

				new Product("4.2")
						.set("title", "string array test")
						.set("category", "foo", "bar"),

				new Product("4.3")
						.set("title", "attribute array test")
						.set("category", Attribute.of("a"), Attribute.of("b")),

				new Product("5")
						.set("title", "all types test")
						.set("string", "foo bar")
						.set("number", 12.5)
						.set("color", Attribute.of("blue"), Attribute.of("red"))
						.set("category", Attribute.of("Men", "_cat1"), Attribute.of("Shoes", "_cat1_1")),

				masterWithVariants(
						(Product) new Product("3").set("title", "master 2"),
						new Document("31"),
						new Document("32").set("price", 99.9).set("price.discount", 78.9),
						new Document("33").set("price", 45.6).set("discountPercentage", "30%")),

				new ImportSession("foo-bar", "foo-bar-20191203"),

				new SortOption("title", SortOrder.ASC, "sort=title"),

				new SearchQuery()
						.setLimit(8)
						.setOffset(42)
						.setSort("sort=margin"),

				new FacetEntry("red", 2, null),

				new Facet("brand").addEntry("nike", 13, "brand=nike"),
				new Facet("categories")
						.addEntry(new HierarchialFacetEntry("a", 50, "categories=a").addChild(new FacetEntry("aa", 23, "categories=aa"))),

				new ResultHit()
						.setDocument(new Document("12").setData(Collections.singletonMap("title", "nice stuff")))
						.setIndex("de-de")
						.setMatchedQueries(new String[] { "nice" }),

				new SearchResultSlice()
						.setMatchCount(42)
						.setNextOffset(8),

				new SearchResult(),

				new SearchResult()
						.setInputQuery(new SearchQuery().setUserQuery("the answer").setSort("wisdom").setLimit(1))
						.setTookInMillis(42L));
	}

	private static Product masterWithVariants(Product masterProduct, Document... variantProducts) {
		masterProduct.setVariants(variantProducts);
		return masterProduct;
	}

	private void assertEqualDocuments(Document expected, Document actual, final String msgPrefix) {
		assertEquals(expected.getId(), actual.getId(), msgPrefix + "IDs not equals");
		expected.getData().forEach((k, v) -> {
			if (v == null) {
				assertNull(actual.getData().get(k));
			}
			else if (v.getClass().isArray()) {
				if (v instanceof int[]) {
					assertArrayEquals((int[]) v, (int[]) actual.getData().get(k), msgPrefix + "value for key '" + k + "' not equals");
				}
				else if (v instanceof double[]) {
					assertArrayEquals((double[]) v, (double[]) actual.getData().get(k), msgPrefix + "value for key '" + k + "' not equals");
				}
				else if (v instanceof long[]) {
					assertArrayEquals((long[]) v, (long[]) actual.getData().get(k), msgPrefix + "value for key '" + k + "' not equals");
				}
				else {
					assertArrayEquals((Object[]) v, (Object[]) actual.getData().get(k), msgPrefix + "value for key '" + k + "' not equals");
				}
			}
			else {
				assertEquals(v, actual.getData().get(k), msgPrefix + "value for key '" + k + "' not equals");
			}
		});

		if (expected instanceof Product) {
			assertThat(actual, instanceOf(Product.class));
			if (((Product) expected).getVariants() == null) {
				assertNull(((Product) actual).getVariants());
			}
			else {
				assertEquals(((Product) expected).getVariants().length, ((Product) actual).getVariants().length, "amount of variants not equal");
				for (int i = 0; i < ((Product) expected).getVariants().length; i++) {
					assertEqualDocuments(((Product) expected).getVariants()[i], ((Product) actual).getVariants()[i], "variant " + i + " not equals");
				}
			}
		}
	}
}
