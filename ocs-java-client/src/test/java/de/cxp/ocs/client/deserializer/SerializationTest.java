package de.cxp.ocs.client.deserializer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.HierarchialFacetEntry;
import de.cxp.ocs.model.result.IntervalFacetEntry;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;
import de.cxp.ocs.model.result.SortOrder;
import de.cxp.ocs.model.result.Sorting;

public class SerializationTest {

	// we don't have control over serialization, so we just can assume standard
	// serialization
	final ObjectMapper serializer = new ObjectMapper();

	ObjectMapper deserializer;

	/**
	 * special configuration for jackson object mapper to work with the
	 * defined models.
	 */
	@BeforeEach
	public void configureDeserialization() {
		deserializer = ObjectMapperFactory.createObjectMapper();
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
				new ImportSession("foo_bar", "ocs-foo_bar-de"),
				
				new Product("1")
						.set("title", "string values test"),
				Attribute.of("a1", "with id"),

				new Attribute("1", "color", "ff0000", "red"),

				new Attribute[] { Attribute.of("1", "fruits"), Attribute.of("2", "apples") },

				new Category("123", "Shoes"),

				new Product("2")
						.set("title", "number values test")
						.set("number", 12.5),

				new Product("3")
						.set("title", "attribute with id test")
						.setAttributes(new Attribute("1", "color", "ff0000", "red")),

				new Product("4.1")
						.set("title", "number array test")
						.set("sizes", 38, 39, 40, 41, 42, 42),

				new Product("4.2")
						.set("title", "string array test")
						.addCategory(Category.of("foo"), Category.of("bar")),

				new Product("4.3")
						.set("title", "attribute array test")
						.setAttributes(Attribute.of("a", "Aha"), Attribute.of("b", "Boha")),

				new Product("5")
						.set("title", "all types test")
						.set("string", "foo bar")
						.set("number", 12.5)
						.setAttributes(Attribute.of("color", "blue"), Attribute.of("color", "red"))
						.addCategory(new Category("1", "Men"), new Category("2", "Shoes"))
						.addCategory(new Category("100", "Sale"), new Category("1", "Men"), new Category("2", "Shoes")),

				masterWithVariants(
						(Product) new Product("3").set("title", "master 2"),
						new Document("31"),
						new Document("32").set("price", 99.9).set("price.discount", 78.9),
						new Document("33").set("price", 45.6).set("discountPercentage", "30%")),

				new ImportSession("foo-bar", "foo-bar-20191203"),

				new Sorting("title", SortOrder.ASC, false, "sort=title"),

				new SearchQuery()
						.setQ("foo")
						.setLimit(8)
						.setOffset(42)
						.setSort("margin"),

				new FacetEntry("red", null, 2, null, false),

				new Facet("brand").addEntry("nike", 13, "brand=nike"),
				new Facet("price").setMeta(Collections.singletonMap("multiSelect", "true"))
						.setType("interval")
						.addEntry(new IntervalFacetEntry(10, 19.99, 123, "price=10,19.99", true)),
				new Facet("categories")
						.addEntry(new HierarchialFacetEntry("a", null, 50, "categories=a", true).addChild(new FacetEntry("aa", null, 23, "categories=aa", false))),

				new ResultHit()
						.setDocument(new Document("12").setData(Collections.singletonMap("title", "nice stuff")))
						.setIndex("de-de")
						.setMatchedQueries(new String[] { "nice" }),

				new SearchResultSlice()
						.setMatchCount(42)
						.setNextOffset(8)
						.setFacets(Arrays.asList(
								new Facet("brand").addEntry(new FacetEntry("puma", null, 9292, "q=shoes", true)),
								new Facet("fake").setFiltered(true),
								new Facet("asd").setAbsoluteFacetCoverage(124)
										.addEntry(new HierarchialFacetEntry("Shoes", null, 124, "cat=shoes", false)
												.addChild(new FacetEntry("Sneakers", null, 12, "cat=shoes,sneakers", false))))),

				new SearchResult(),

				new SearchResult()
						.setInputURI(new SearchQuery().setQ("the answer").setSort("wisdom").setLimit(1).asUri())
						.setTookInMillis(42L));
	}

	private static Product masterWithVariants(Product masterProduct, Document... variantProducts) {
		masterProduct.setVariants(variantProducts);
		return masterProduct;
	}

	private void assertEqualDocuments(Document expected, Document actual, final String msgPrefix) {
		assertEquals(expected.getId(), actual.getId(), msgPrefix + "IDs not equal");

		assertArrayEquals(expected.getAttributes(), actual.getAttributes(), msgPrefix + "Attributes not equal");

		if (expected.getCategories() == null) {
			assertNull(actual.getCategories());
		}
		else {
			assertEquals(expected.getCategories().size(), actual.getCategories().size(), msgPrefix + "Categories not equal in size");
			for (int i = 0; i < expected.getCategories().size(); i++) {
				assertArrayEquals(expected.getCategories().get(i), actual.getCategories().get(i),
						msgPrefix + "Category at index " + i + " not equal");
			}
		}

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
			assertTrue(actual instanceof Product);
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
