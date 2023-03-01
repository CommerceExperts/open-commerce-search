package de.cxp.ocs.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TestUtils {

	private TestUtils() {}

	public static <T> T assertAndCastInstanceOf(Object term, Class<T> clazz) {
		assertTrue(clazz.isAssignableFrom(term.getClass()), "expected object of type " + clazz + " but was " + term.getClass());
		return clazz.cast(term);
	}
}
