package de.cxp.ocs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Hierarchy;
import de.cxp.ocs.model.index.HierarchyLevel;
import de.cxp.ocs.model.index.Product;
import de.cxp.ocs.model.params.NumberResultFilter;
import de.cxp.ocs.model.params.PathResultFilter;
import de.cxp.ocs.model.params.ResultFilter;
import de.cxp.ocs.model.params.SearchParams;
import de.cxp.ocs.model.params.SortOrder;
import de.cxp.ocs.model.params.Sorting;
import de.cxp.ocs.model.params.TermResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.HierarchialFacetEntry;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;

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

		deserializer.addMixIn(HierarchyLevel.class, SingleStringArgsCreator.class);
		deserializer.addMixIn(Hierarchy.class, CategoryPathCreator.class);
		deserializer.addMixIn(Facet.class, FacetMixin.class);

		deserializer.addMixIn(ResultFilter.class, WithTypeInfo.class);
		deserializer.registerSubtypes(NumberResultFilter.class, TermResultFilter.class, PathResultFilter.class);

		deserializer.addMixIn(FacetEntry.class, WithTypeInfo.class);
		deserializer.registerSubtypes(HierarchialFacetEntry.class);
	}

	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "_type")
	public static abstract class WithTypeInfo {}

	public static abstract class SingleStringArgsCreator {

		@JsonCreator
		SingleStringArgsCreator(String name) {}
	}

	public static abstract class CategoryPathCreator {

		@JsonCreator
		CategoryPathCreator(HierarchyLevel[] categories) {}
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
		assertEquals(serializable, deserialized);
	}

	public static Stream<Object> getSerializableObjects() {
		return Stream.of(
				new HierarchyLevel("cat only"),
				new HierarchyLevel("a1", "with id"),

				Hierarchy.simplePath("single level"),
				Hierarchy.simplePath("many", "level", "category"),
				new Hierarchy(new HierarchyLevel[] { new HierarchyLevel("1", "fruits"), new HierarchyLevel("2", "apples") }),

				new Product("1").putData("title", "master test"),

				new Product("2").putData("title", "master 2")
						.putData("string", "foo bar")
						.putData("number", 12.5),

				new Product("3")
						.putData("title", "master category test")
						.putData("category", Hierarchy.simplePath("a", "b")),

				new Product("4")
						.putData("title", "master category test ")
						.putData("category", new HierarchyLevel[] { new HierarchyLevel("fruit"), new HierarchyLevel("apple") }),

				masterWithVariants(
						(Product) new Product("3").putData("title", "master 2"),
						new Document("31"),
						new Document("32").putData("price", 99.9).putData("price.discount", 78.9),
						new Document("33").putData("price", 45.6).putData("type", "var1")),

				new ImportSession("foo-bar", "foo-bar-20191203"),

				new NumberResultFilter("price", 10.1, 99.9),
				new PathResultFilter("category", new String[] { "a", "b", "c" }),
				new TermResultFilter("color", "blue"),
				new TermResultFilter("color", "red", "black"),

				new Sorting("title", SortOrder.ASC),

				new SearchParams()
						.setLimit(8)
						.setOffset(42)
						.withFilter(new NumberResultFilter("price", 10.1, 99.9))
						.withFilter(new PathResultFilter("category", new String[] { "a", "b", "c" }))
						.withFilter(new TermResultFilter("color", "red", "black"))
						.withSorting(new Sorting("margin", SortOrder.DESC)),

				new Facet("brand").addEntry("nike", 13),
				new Facet("categories")
						.addEntry(new HierarchialFacetEntry("a", 50).addChild(new FacetEntry("aa", 23))),

				new ResultHit()
						.setDocument(new Document("12").setData(Collections.singletonMap("title", "nice stuff")))
						.setIndex("de-de")
						.setMatchedQueries(new String[] { "nice" }),

				new SearchResult(),

				new SearchResult()
						.setSearchQuery("the answer")
						.setMatchCount(42)
						.setNextOffset(8)
						.setTookInMillis(400000000000L)

		);
	}

	private static Product masterWithVariants(Product masterProduct, Document... variantProducts) {
		masterProduct.setVariants(variantProducts);
		return masterProduct;
	}

}
