package de.cxp.ocs.elasticsearch.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class NonAlphanumericStripPreprocessorTest {

	final NonAlphanumericStripPreprocessor underTest = new NonAlphanumericStripPreprocessor();

	@Test
	public void testCommaSplit() {
		assertEquals("123 345", underTest.preProcess("123,345"));
		assertEquals("123 345 678", underTest.preProcess("123,345,678"));
	}

	@Test
	public void testDualCommaIgnored() {
		assertEquals("123 345", underTest.preProcess("123,,345"));
	}

	@Test
	public void testCombinedSeparators() {
		assertEquals("lounge 4-tlg", underTest.preProcess("lounge, 4-tlg."));
	}

	@Test
	public void testDelimiterAtEnd() {
		assertEquals("123 345", underTest.preProcess("123,345,"));
	}

	@Test
	public void testDifferentDelimitersCombined() {
		assertEquals("123 345", underTest.preProcess("123, .:345"));
		assertEquals("123 345", underTest.preProcess("123,.:345"));
		assertEquals("123 345", underTest.preProcess("123,:345"));
		assertEquals("123 345", underTest.preProcess("123:345"));

		// certain binding characters are not removed if in between two proper tokens
		assertEquals("123.345", underTest.preProcess("123.345"));
		assertEquals("m.2", underTest.preProcess("m.2"));
	}

	@Test
	public void testWordBindCharactersAreNotSplit() {
		assertEquals("hochvolt-schiene 50w", underTest.preProcess("hochvolt-schiene 50w"));
		assertEquals("hochvolt_schiene 50w", underTest.preProcess("hochvolt_schiene 50w"));
	}

	@Test
	public void testBindCharactersAreTrimmed() {
		assertEquals("tld", underTest.preProcess("tld."));
		assertEquals("bind", underTest.preProcess("bind-"));
		assertEquals("under", underTest.preProcess("under_"));
	}

	@Test
	public void testSingleBindCharactersAreRemoved() {
		assertEquals("hochvolt schiene", underTest.preProcess("hochvolt - schiene"));
	}

	@Test
	public void testQuotesAreRemoved() {
		assertEquals("monitor 15.6", underTest.preProcess("monitor 15.6\""));
		assertEquals("model superduper", underTest.preProcess("model \"superduper\""));
	}

	@Test
	public void testAsianCharacters() {
		assertEquals("牌信誉", underTest.preProcess("牌信誉"));
	}

	@Test
	public void testEuropeanSpecialCharacters() {
		for (String query : new String[] { "Boîte à outils", "búsqueda", "Šport a voľný čas", "megkönnyíthetjük" }) {
			assertEquals(query.toLowerCase(), underTest.preProcess(query));
		}
	}

	@Test
	public void testDimensionsSeparatorX() {
		assertEquals("12x34", underTest.preProcess("12x34"));
	}

	@Test
	public void testDimensionsSeparatorAstrx() {
		assertEquals("12 34", underTest.preProcess("12*34"));
	}
}
