package de.cxp.ocs.preprocessor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;

import org.elasticsearch.core.List;
import org.elasticsearch.core.Map;
import org.junit.jupiter.api.Test;

import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Document;

public class AttributeToDataFieldConverterTest {

	private AttributeToDataFieldConverter underTest = new AttributeToDataFieldConverter();

	@Test
	public void withoutConfig() {
		underTest = new AttributeToDataFieldConverter();
		Document doc = new Document("1")
				.addAttribute(Attribute.of("color", "red"))
				.addAttribute(Attribute.of("color", "black"))
				.addAttribute(Attribute.of("size", "Large").setCode("L"));
		assertTrue(underTest.process(doc, true));

		assertIterableEquals(List.of("red", "black"), (Collection<?>) doc.getData().get("color"));
		assertNull(doc.getData().get("size"));
	}

	@Test
	public void withInclude() {
		underTest = new AttributeToDataFieldConverter();
		underTest.initialize(null, Map.of("include", "size"));

		Document doc = new Document("1")
				.addAttribute(Attribute.of("color", "red"))
				.addAttribute(Attribute.of("color", "black"))
				.addAttribute(Attribute.of("size", "Large").setCode("L"));
		assertTrue(underTest.process(doc, true));

		assertEquals("Large", doc.getData().get("size"));
		assertNull(doc.getData().get("color"));
	}

	@Test
	public void withExclude() {
		underTest = new AttributeToDataFieldConverter();
		underTest.initialize(null, Map.of("exclude", "color"));

		Document doc = new Document("1")
				.addAttribute(Attribute.of("color", "red"))
				.addAttribute(Attribute.of("color", "black"))
				.addAttribute(Attribute.of("size", "Large").setCode("L"));
		assertTrue(underTest.process(doc, true));

		assertNull(doc.getData().get("color"));
		// without include, an attribute with code is not converted
		assertNull(doc.getData().get("size"));
	}
}
