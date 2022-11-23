package de.cxp.ocs.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.config.*;
import de.cxp.ocs.indexer.model.FacetEntry;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.util.MinMaxSet;

public class IndexItemConverterTest {

	IndexItemConverter underTest;

	@Test
	public void textFieldUsedForSearch() {
		underTest = new IndexItemConverter(
				new FieldConfigIndex(
						new FieldConfiguration()
								.addField(new Field("title").setUsage(FieldUsage.SEARCH))));

		IndexableItem result = underTest.toIndexableItem(new Document("1").set("title", "foo"));
		assertEquals("foo", result.getSearchData().get("title"));

		assertTrue(result.getPathFacetData().isEmpty());
		assertTrue(result.getNumberFacetData().isEmpty());
		assertTrue(result.getResultData().isEmpty());
		assertTrue(result.getScores().isEmpty());
		assertTrue(result.getTermFacetData().isEmpty());
		assertTrue(result.getSortData().isEmpty());
	}

	@Test
	public void numericFieldUsedForFacet() {
		underTest = new IndexItemConverter(
				new FieldConfigIndex(
						new FieldConfiguration()
								.addField(new Field("size").setUsage(FieldUsage.FACET).setType(FieldType.NUMBER))));

		IndexableItem result = underTest.toIndexableItem(new Document("2").set("size", "34"));
		assertEquals("size", result.getNumberFacetData().get(0).getName());
		assertEquals(34, result.getNumberFacetData().get(0).getValue());

		assertTrue(result.getSearchData().isEmpty());
		assertTrue(result.getPathFacetData().isEmpty());
		assertTrue(result.getResultData().isEmpty());
		assertTrue(result.getScores().isEmpty());
		assertTrue(result.getTermFacetData().isEmpty());
		assertTrue(result.getSortData().isEmpty());
	}

	@Test
	public void textFieldUsedForResultAndFacet() {
		underTest = new IndexItemConverter(
				new FieldConfigIndex(
						new FieldConfiguration()
								.addField(new Field("brand").setUsage(FieldUsage.RESULT, FieldUsage.FACET))));

		IndexableItem result = underTest.toIndexableItem(new Document("1").set("brand", "fancy"));
		assertEquals("fancy", result.getResultData().get("brand"));
		assertEquals("brand", result.getTermFacetData().get(0).getName());
		assertEquals("fancy", result.getTermFacetData().get(0).getValue());

		assertTrue(result.getSearchData().isEmpty());
		assertTrue(result.getPathFacetData().isEmpty());
		assertTrue(result.getNumberFacetData().isEmpty());
		assertTrue(result.getScores().isEmpty());
		assertTrue(result.getSortData().isEmpty());
	}

	@Test
	public void numericFieldUsedForScoring() {
		underTest = new IndexItemConverter(
				new FieldConfigIndex(
						new FieldConfiguration()
								.addField(new Field("rating").setUsage(FieldUsage.SCORE))));

		IndexableItem result = underTest.toIndexableItem(new Document("1").set("rating", "4.56"));
		assertEquals(4.56f, result.getScores().get("rating"));

		assertTrue(result.getSearchData().isEmpty());
		assertTrue(result.getPathFacetData().isEmpty());
		assertTrue(result.getNumberFacetData().isEmpty());
		assertTrue(result.getResultData().isEmpty());
		assertTrue(result.getTermFacetData().isEmpty());
		assertTrue(result.getSortData().isEmpty());

		// not true anymore, since a date-string is also accepted as scoring value
		// TODO: test date string for scoring
//		assertThrows(Exception.class,
//				() -> underTest.toIndexableItem(new Document("2").set("rating", "invalid content")),
//				"document with invalid rating field should cause exception");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void numericFieldUsedForSortAndFacet() {
		underTest = new IndexItemConverter(
				new FieldConfigIndex(
						new FieldConfiguration()
								.addField(new Field("price").setUsage(FieldUsage.SORT, FieldUsage.FACET).setType(FieldType.NUMBER))));

		IndexableItem result = underTest.toIndexableItem(new Document("1").set("price", "99.5"));
		assertEquals(99.5f, ((MinMaxSet<Float>) result.getSortData().get("price")).min());
		assertEquals(99.5f, ((MinMaxSet<Float>) result.getSortData().get("price")).max());
		assertEquals("price", result.getNumberFacetData().get(0).getName());
		assertEquals(99.5f, result.getNumberFacetData().get(0).getValue());

		assertTrue(result.getSearchData().isEmpty());
		assertTrue(result.getPathFacetData().isEmpty());
		assertTrue(result.getResultData().isEmpty());
		assertTrue(result.getScores().isEmpty());
		assertTrue(result.getTermFacetData().isEmpty());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void sourceNamesConsidered() {
		underTest = new IndexItemConverter(
				new FieldConfigIndex(
						new FieldConfiguration()
								.addField(new Field("price")
										.setType(FieldType.NUMBER)
										.setUsage(FieldUsage.FACET)
										.setSourceNames(Arrays.asList("PRICES")))
								.addField(new Field("title")
										.setUsage(FieldUsage.SEARCH, FieldUsage.RESULT)
										.setSourceNames(Arrays.asList("TITLES")))
								.addField(new Field("rating")
										.setUsage(FieldUsage.SCORE, FieldUsage.SORT)
										.setSourceNames(Arrays.asList("RATINGS")))));

		IndexableItem result = underTest.toIndexableItem(
				new Document("1")
						.set("PRICES", "19.5")
						.set("TITLES", "fnord")
						.set("RATINGS", "45.2")
						.set("brand", "ignored"));
		assertEquals("45.2", ((MinMaxSet<String>) result.getSortData().get("rating")).min());
		assertEquals("45.2", ((MinMaxSet<String>) result.getSortData().get("rating")).max());

		assertEquals("price", result.getNumberFacetData().get(0).getName());
		assertEquals(19.5f, result.getNumberFacetData().get(0).getValue());

		assertEquals("fnord", result.getSearchData().get("title"));
		assertEquals("fnord", result.getResultData().get("title"));

		assertEquals(45.2f, result.getScores().get("rating"));

		assertEquals("1", result.getId());

		assertTrue(result.getPathFacetData().isEmpty());
		assertTrue(result.getTermFacetData().isEmpty());
	}

	@Test
	public void testDataFieldMatchesNormalFieldAndDynamicField() {
		// A normal field and a dynamic field are defined and some fields
		// match both of them.
		// Assert that exact field matches are always preferred and only one
		// field definition is used
		underTest = new IndexItemConverter(
				new FieldConfigIndex(
						new FieldConfiguration()
								.addField(new Field("text").setUsage(FieldUsage.SEARCH).addSourceName("TEXT"))
								.addDynamicField(new Field("text_size").setUsage(FieldUsage.RESULT).addSourceName("t.*"))));

		IndexableItem result1 = underTest.toIndexableItem(new Document("1").set("text", "one"));
		assertEquals("one", result1.getSearchData().get("text"));
		assertTrue(result1.getResultData().isEmpty());

		IndexableItem result2 = underTest.toIndexableItem(new Document("2").set("TEXT", "two"));
		assertEquals("two", result2.getSearchData().get("text"));
		assertTrue(result2.getResultData().isEmpty());

		IndexableItem result3 = underTest.toIndexableItem(new Document("3").set("text_size", "three"));
		assertTrue(result3.getSearchData().isEmpty());
		assertEquals("three", result3.getResultData().get("text_size"));
	}

	/**
	 * if an attribute label matches a field name exactly, only that field
	 * configuration is considered. Dynamic matching fields are ignored.
	 */
	@Test
	public void testAttributeMatchesFieldName() {
		underTest = new IndexItemConverter(
				new FieldConfigIndex(
						new FieldConfiguration()
								.addField(new Field("color").setUsage(FieldUsage.SEARCH))
								.addDynamicField(new Field("attributes").setUsage(FieldUsage.FACET))));

		IndexableItem result = underTest.toIndexableItem(new Document("1")
				.setAttributes(new Attribute().setName("color").setValue("red")));
		assertEquals("red", result.getSearchData().get("color"));
		assertTrue(result.getTermFacetData().isEmpty());
	}

	@Test
	public void testAttributeMatchesDynamicField() {
		underTest = new IndexItemConverter(
				new FieldConfigIndex(
						new FieldConfiguration()
								.addField(new Field("title").setUsage(FieldUsage.SEARCH))
								.addDynamicField(new Field("attributes").addSourceName(".*").setUsage(FieldUsage.FACET))));

		IndexableItem result = underTest.toIndexableItem(new Document("1")
				.setAttributes(new Attribute().setName("color").setValue("red")));
		assertTrue(result.getSearchData().isEmpty());
		assertEquals("color", result.getTermFacetData().get(0).getName());
		assertEquals("red", result.getTermFacetData().get(0).getValue());
	}

	@Test
	public void testAttributeMatchesDynamicFieldByType() {
		underTest = new IndexItemConverter(
				new FieldConfigIndex(
						new FieldConfiguration()
								// this pattern at the standard field MUST NOT
								// work!
								.addField(new Field("title").addSourceName(".*").setUsage(FieldUsage.SEARCH))
								// special case: if field is named "attribute"
								// it will
								// only apply to attribute data and not to other
								// unknown
								// fields
								.addDynamicField(new Field("attribute").setType(FieldType.NUMBER).setUsage(FieldUsage.FACET))
								.addDynamicField(new Field("attribute").setType(FieldType.STRING).setUsage(FieldUsage.FACET))));

		IndexableItem result = underTest.toIndexableItem(new Document("1")
				.set("unknown", "must be ignored")
				.setAttributes(
						new Attribute().setName("color").setValue("red"),
						new Attribute().setName("size").setValue("41.5")));

		assertTrue(result.getSearchData().isEmpty());
		assertTrue(result.getResultData().isEmpty());

		assertEquals(1, result.getTermFacetData().size());
		assertEquals("color", result.getTermFacetData().get(0).getName());
		assertEquals("red", result.getTermFacetData().get(0).getValue());

		assertEquals(1, result.getNumberFacetData().size());
		assertEquals("size", result.getNumberFacetData().get(0).getName());
		assertEquals(41.5f, result.getNumberFacetData().get(0).getValue());
	}

	/**
	 * If no dynamic field is configured, attributes will be dropped (unless
	 * they match a exact field name)
	 */
	@Test
	public void testAttributeMatchesNoField() {
		underTest = new IndexItemConverter(
				new FieldConfigIndex(
						new FieldConfiguration()
								.addField(new Field("title").setUsage(FieldUsage.SEARCH))));

		IndexableItem result = underTest.toIndexableItem(new Document("1")
				.setAttributes(new Attribute().setName("color").setValue("red")));

		assertTrue(result.getSearchData().isEmpty());
		assertTrue(result.getPathFacetData().isEmpty());
		assertTrue(result.getResultData().isEmpty());
		assertTrue(result.getScores().isEmpty());
		assertTrue(result.getTermFacetData().isEmpty());
		assertTrue(result.getNumberFacetData().isEmpty());
	}

	@Test
	public void testStandardFieldAsCategoryField() {
		underTest = new IndexItemConverter(
				new FieldConfigIndex(
						new FieldConfiguration()
								.addField(new Field("category").setUsage(FieldUsage.FACET).setType(FieldType.CATEGORY))));

		IndexableItem result = underTest.toIndexableItem(new Document("1")
				.set("category", "foo/bar"));

		assertEquals(result.getPathFacetData().get(0), new FacetEntry<>("category", "foo"));
		assertEquals(result.getPathFacetData().get(1), new FacetEntry<>("category", "foo/bar"));
		assertEquals(result.getPathFacetData().size(), 2);

		assertTrue(result.getSearchData().isEmpty());
		assertTrue(result.getResultData().isEmpty());
		assertTrue(result.getScores().isEmpty());
		assertTrue(result.getTermFacetData().isEmpty());
		assertTrue(result.getNumberFacetData().isEmpty());
	}

	@Test
	public void testDocumentCategoriesWithId() {
		underTest = new IndexItemConverter(
				new FieldConfigIndex(
						new FieldConfiguration()
								.addField(new Field("category").setUsage(FieldUsage.FACET).setType(FieldType.CATEGORY))));

		IndexableItem result = underTest.toIndexableItem(new Document("1")
				.addCategory(
						new Category().setName("foo").setId("12"),
						new Category().setName("bar").setId("23")));

		assertEquals(result.getPathFacetData().size(), 2);
		assertEquals(result.getPathFacetData().get(0), new FacetEntry<>("category", "foo").setId("12"));
		assertEquals(result.getPathFacetData().get(1), new FacetEntry<>("category", "foo/bar").setId("23"));

		assertTrue(result.getSearchData().isEmpty());
		assertTrue(result.getResultData().isEmpty());
		assertTrue(result.getScores().isEmpty());
		assertTrue(result.getTermFacetData().isEmpty());
		assertTrue(result.getNumberFacetData().isEmpty());
	}

	@Test
	public void testMutlipleCategoryPathsWithId() {
		underTest = new IndexItemConverter(
				new FieldConfigIndex(
						new FieldConfiguration()
								.addField(new Field("category").setUsage(FieldUsage.FACET).setType(FieldType.CATEGORY))));

		IndexableItem result = underTest.toIndexableItem(new Document("1")
				.addCategory(
						new Category().setName("foo").setId("12"),
						new Category().setName("bar").setId("23"))
				.addCategory(
						new Category().setName("another").setId("7"),
						new Category().setName("one").setId("8"),
						new Category().setName("as well").setId("9")));

		assertEquals(result.getPathFacetData().size(), 5);
		assertEquals(result.getPathFacetData().get(0), new FacetEntry<>("category", "foo").setId("12"));
		assertEquals(result.getPathFacetData().get(1), new FacetEntry<>("category", "foo/bar").setId("23"));
		assertEquals(result.getPathFacetData().get(2), new FacetEntry<>("category", "another").setId("7"));
		assertEquals(result.getPathFacetData().get(3), new FacetEntry<>("category", "another/one").setId("8"));
		assertEquals(result.getPathFacetData().get(4), new FacetEntry<>("category", "another/one/as well").setId("9"));

		assertTrue(result.getSearchData().isEmpty());
		assertTrue(result.getResultData().isEmpty());
		assertTrue(result.getScores().isEmpty());
		assertTrue(result.getTermFacetData().isEmpty());
		assertTrue(result.getNumberFacetData().isEmpty());
	}

	@Test
	public void testMutlipleCategoryFields() {
		underTest = new IndexItemConverter(
				new FieldConfigIndex(
						new FieldConfiguration()
								.addField(new Field("category").setUsage(FieldUsage.FACET).setType(FieldType.CATEGORY))
								.addField(new Field("color").setUsage(FieldUsage.FACET).setType(FieldType.CATEGORY))));

		IndexableItem result = underTest.toIndexableItem(new Document("1")
				.set("color", "red/dark red/bordeaux")
				.addCategory(
						new Category().setName("foo").setId("12"),
						new Category().setName("bar").setId("23")));

		assertEquals(result.getPathFacetData().size(), 5);
		assertEquals(result.getPathFacetData().get(0), new FacetEntry<>("color", "red"));
		assertEquals(result.getPathFacetData().get(1), new FacetEntry<>("color", "red/dark red"));
		assertEquals(result.getPathFacetData().get(2), new FacetEntry<>("color", "red/dark red/bordeaux"));
		assertEquals(result.getPathFacetData().get(3), new FacetEntry<>("category", "foo").setId("12"));
		assertEquals(result.getPathFacetData().get(4), new FacetEntry<>("category", "foo/bar").setId("23"));

		assertTrue(result.getSearchData().isEmpty());
		assertTrue(result.getResultData().isEmpty());
		assertTrue(result.getScores().isEmpty());
		assertTrue(result.getTermFacetData().isEmpty());
		assertTrue(result.getNumberFacetData().isEmpty());
	}
}
