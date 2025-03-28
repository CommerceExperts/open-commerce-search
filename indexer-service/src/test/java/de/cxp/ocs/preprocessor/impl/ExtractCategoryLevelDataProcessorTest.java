package de.cxp.ocs.preprocessor.impl;

import de.cxp.ocs.config.*;
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.model.index.Document;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExtractCategoryLevelDataProcessorTest {

	@SuppressWarnings("deprecation")
	@Test
	public void testPrimaryCategoryFieldExtraction() {
		var underTest = new ExtractCategoryLevelDataProcessor();
		underTest.initialize(new FieldConfigIndex(new FieldConfiguration()
						.addField(new Field("category").setType(FieldType.CATEGORY))),
				Map.of());

		var doc = new Document("1").addCategory(new Category("1", "Aaa"), new Category("1.1", "Aaa.aa"));
		assert underTest.process(doc, true);
		assertEquals("Aaa", doc.getData().get("category_lvl_0"));
		assertEquals("Aaa.aa", doc.getData().get("category_lvl_1"));
		assertEquals("Aaa.aa", doc.getData().get("category_leaf"));
		doc.data.clear();

		// add another path to see how multi-value works
		doc.addCategory(new Category("2", "BBb"), new Category("2.1", "BBb.bb"));

		assert underTest.process(doc, true);
		assertEquals("Aaa BBb", doc.getData().get("category_lvl_0"));
		assertEquals("Aaa.aa BBb.bb", doc.getData().get("category_lvl_1"));
		assertEquals("Aaa.aa BBb.bb", doc.getData().get("category_leaf"));
	}

	@Test
	public void testCategorySetInData() {
		var underTest = new ExtractCategoryLevelDataProcessor();
		underTest.initialize(new FieldConfigIndex(new FieldConfiguration()
						.addField(new Field("category").setType(FieldType.CATEGORY))),
				Map.of());

		var doc = new Document("1").addPath("category", new Category("1", "Aaa"), new Category("1.1", "Aaa.aa"));

		assert underTest.process(doc, true);
		assertEquals("Aaa", doc.getData().get("category_lvl_0"));
		assertEquals("Aaa.aa", doc.getData().get("category_lvl_1"));
		assertEquals("Aaa.aa", doc.getData().get("category_leaf"));
	}

	@Test
	public void testDualCategoryPathsWithTwoWays() {
		var underTest = new ExtractCategoryLevelDataProcessor();
		underTest.initialize(new FieldConfigIndex(new FieldConfiguration()
						.addField(new Field("category").setType(FieldType.CATEGORY))
						.addField(new Field("taxonomy").setType(FieldType.CATEGORY))),
				Map.of());

		var doc = new Document("1")
				.addCategory(new Category("1", "Aaa"), new Category("1.1", "Aaa.aa")) // legacy way
				.addPath("taxonomy", new Category("t1", "t.a"), new Category("t1.1", "t.a.b"));

		assert underTest.process(doc, true);
		assertEquals("Aaa", doc.getData().get("category_lvl_0"));
		assertEquals("Aaa.aa", doc.getData().get("category_lvl_1"));
		assertEquals("Aaa.aa", doc.getData().get("category_leaf"));

		assertEquals("t.a", doc.getData().get("taxonomy_lvl_0"));
		assertEquals("t.a.b", doc.getData().get("taxonomy_lvl_1"));
		assertEquals("t.a.b", doc.getData().get("taxonomy_leaf"));
	}

	@Test
	public void testDualCategoryPaths() {
		var underTest = new ExtractCategoryLevelDataProcessor();
		underTest.initialize(new FieldConfigIndex(new FieldConfiguration()
						.addField(new Field("category").setType(FieldType.CATEGORY))
						.addField(new Field("taxonomy").setType(FieldType.CATEGORY))),
				Map.of());

		var doc = new Document("1")
				.addPath("category", new Category("1", "Aaa"), new Category("1.1", "Aaa.aa")) // legacy way
				.addPath("taxonomy", new Category("t1", "t.a"), new Category("t1.1", "t.a.b"));

		assert underTest.process(doc, true);
		assertEquals("Aaa", doc.getData().get("category_lvl_0"));
		assertEquals("Aaa.aa", doc.getData().get("category_lvl_1"));
		assertEquals("Aaa.aa", doc.getData().get("category_leaf"));

		assertEquals("t.a", doc.getData().get("taxonomy_lvl_0"));
		assertEquals("t.a.b", doc.getData().get("taxonomy_lvl_1"));
		assertEquals("t.a.b", doc.getData().get("taxonomy_leaf"));
	}
}
