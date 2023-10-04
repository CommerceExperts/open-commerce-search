package de.cxp.ocs.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

public class FieldTest {

	@Test
	public void noUsagesSet() {
		Field underTest = new Field();
		assertFalse(underTest.hasUsage(FieldUsage.SEARCH));
	}

	@Test
	public void setEmptyUsage() {
		Field underTest = new Field();
		underTest.setUsage(null);
		assertFalse(underTest.hasUsage(FieldUsage.SEARCH));

		underTest.setUsage(Collections.emptyList());
		assertFalse(underTest.hasUsage(FieldUsage.SEARCH));
	}

	@Test
	public void setSingleUsage() {
		Field underTest = new Field();
		underTest.setUsage(FieldUsage.RESULT);
		assertFalse(underTest.hasUsage(FieldUsage.SEARCH));
		assertTrue(underTest.hasUsage(FieldUsage.RESULT));
	}

	@Test
	public void setManyUsages() {
		Field underTest = new Field("all");
		underTest.setUsage(FieldUsage.RESULT, FieldUsage.FACET);
		assertFalse(underTest.hasUsage(FieldUsage.SEARCH));
		assertTrue(underTest.hasUsage(FieldUsage.RESULT));
		assertTrue(underTest.hasUsage(FieldUsage.FACET));
	}

	@Test
	public void setUsageCollection() {
		Field underTest = new Field("all");
		underTest.setUsage(Arrays.asList(FieldUsage.RESULT, FieldUsage.FACET));
		assertFalse(underTest.hasUsage(FieldUsage.SEARCH));
		assertTrue(underTest.hasUsage(FieldUsage.RESULT));
		assertTrue(underTest.hasUsage(FieldUsage.FACET));
	}
}
