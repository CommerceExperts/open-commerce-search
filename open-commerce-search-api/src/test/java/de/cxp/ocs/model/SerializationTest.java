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
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.model.index.CategoryPath;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.MasterProduct;
import de.cxp.ocs.model.index.VariantProduct;
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

	final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * special configuration for jackson object mapper to work with the
	 * defined models.
	 */
	@BeforeEach
	public void optimizeSerialization() {
		objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		objectMapper.setSerializationInclusion(Include.NON_NULL);
		objectMapper.setSerializationInclusion(Include.NON_EMPTY);

		objectMapper.registerModule(new ParameterNamesModule(Mode.PROPERTIES));

		objectMapper.addMixIn(Category.class, SingleStringArgsCreator.class);
		objectMapper.addMixIn(CategoryPath.class, CategoryPathCreator.class);
		objectMapper.addMixIn(VariantProduct.class, SingleStringArgsCreator.class);
		objectMapper.addMixIn(Facet.class, FacetMixin.class);

		objectMapper.addMixIn(ResultFilter.class, WithTypeInfo.class);
		objectMapper.registerSubtypes(NumberResultFilter.class, TermResultFilter.class, PathResultFilter.class);

		objectMapper.addMixIn(FacetEntry.class, WithTypeInfo.class);
		objectMapper.registerSubtypes(HierarchialFacetEntry.class);
	}
	
	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
	public static abstract class WithTypeInfo {}

	public static abstract class SingleStringArgsCreator {
		@JsonCreator
		SingleStringArgsCreator(String name) {}
	}

	public static abstract class CategoryPathCreator {
		@JsonCreator
		CategoryPathCreator(Category[] categories) {}
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
		String serialized = objectMapper.writeValueAsString(serializable);
		System.out.println(serialized);
		Object deserialized = objectMapper.readValue(serialized, serializable.getClass());
		assertEquals(serializable, deserialized);
	}

	public static Stream<Object> getSerializableObjects() {
		return Stream.of(
				new Category("cat only"),
				new Category("a1", "with id"),

				CategoryPath.simplePath("single level"),
				CategoryPath.simplePath("many", "level", "category"),
				new CategoryPath(new Category[] { new Category("1", "fruits"), new Category("2", "apples") }),

				new MasterProduct("1", "master test"),
				new MasterProduct("2", "master 2").addData("foo", "bar").addData("number", 12.5),
				new MasterProduct("3", "master category test").setCategoryPaths(new CategoryPath[] { CategoryPath.simplePath("a", "b") }),

				new VariantProduct("1"),
				new VariantProduct("201").setPrice(99.9).setPrices(Collections.singletonMap("discount", 78.9)),
				new VariantProduct("3001").setPrice(45.6).addData("type", "var1"),

				masterWithVariants(
						new MasterProduct("3", "master 2"),
						new VariantProduct("31"),
						new VariantProduct("32").setPrice(99.9).setPrices(Collections.singletonMap("discount", 78.9)),
						(VariantProduct) new VariantProduct("33").setPrice(45.6).addData("type", "var1")),

				new ImportSession(System.currentTimeMillis(), "foo-bar", "foo-bar-20191203"),

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

	private static MasterProduct masterWithVariants(MasterProduct masterProduct, VariantProduct... variantProducts) {
		masterProduct.setVariants(variantProducts);
		return masterProduct;
	}

}
