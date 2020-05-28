package io.searchhub.smartsuggest.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;

import org.junit.jupiter.api.Test;

public class UtilTest {

	@Test
	public void testCommonChars() {
		assertEquals(0, commonChars("foo", "bar"));
		assertEquals(1.0, commonChars("!foo 12", " !foo 12"));
		assertEquals(0.75, commonChars("fool", " olf"));
		assertEquals(0.5, commonChars("abcd", " zbdw"));
		assertEquals(0, commonChars("foo", ""));
		assertEquals(0, commonChars("", "foo"));
		assertEquals(0, commonChars(" ", "   "));
		assertEquals(1.0, commonChars("Foo", " foo"));
	}

	@Test
	public void testAlphaNonAsciiChars() {
		// XXX nice to have: make ASCII counterparts are considered a bit more similar
		//assertTrue(commonChars("rene", "rené") > commonChars("reno", "rené"));
		assertTrue(commonChars("rene", "rené") == commonChars("reno", "rené"));
		
		assertTrue(commonChars("réné", "rené") == commonChars("rene", "rené"));
	}
	
	private double commonChars(String input, String target) {
		return Util.commonChars(Locale.ROOT, input, target);
	}
	
}
