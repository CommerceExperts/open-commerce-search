package de.cxp.ocs.elasticsearch.query.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

public class NonAlphanumericWordSplitAnalyzerTest {

	final NonAlphanumericWordSplitAnalyzer underTest = new NonAlphanumericWordSplitAnalyzer();

	@Test
	public void testCommaSplit() {
		assertEquals(Arrays.asList("123", "345"), underTest.analyze("123,345").getInputTerms());
		assertEquals(Arrays.asList("123", "345", "678"), underTest.analyze("123,345,678").getInputTerms());
	}

	@Test
	public void testDualCommaIgnored() {
		assertEquals(Arrays.asList("123", "345"), underTest.analyze("123,,345").getInputTerms());
	}

	@Test
	public void testCombinedSeparators() {
		assertEquals(Arrays.asList("lounge", "4-tlg"), underTest.analyze("lounge, 4-tlg.").getInputTerms());
	}

	@Test
	public void testDelimiterAtEnd() {
		assertEquals(Arrays.asList("123", "345"), underTest.analyze("123,345,").getInputTerms());
	}

	@Test
	public void testDifferentDelimitersCombined() {
		assertEquals(Arrays.asList("123", "345"), underTest.analyze("123, .:345").getInputTerms());
		assertEquals(Arrays.asList("123", "345"), underTest.analyze("123,.:345").getInputTerms());
		assertEquals(Arrays.asList("123", "345"), underTest.analyze("123,:345").getInputTerms());
		assertEquals(Arrays.asList("123", "345"), underTest.analyze("123:345").getInputTerms());
	}

	@Test
	public void testWordBindCharactersAreNotSplit() {
		assertEquals(Arrays.asList("hochvolt-schiene", "50w"), underTest.analyze("hochvolt-schiene 50w").getInputTerms());
		assertEquals(Arrays.asList("hochvolt_schiene", "50w"), underTest.analyze("hochvolt_schiene 50w").getInputTerms());
	}

	@Test
	public void testBindCharactersAreTrimmed() {
		assertEquals(List.of("tld"), underTest.analyze("tld.").getInputTerms());
		assertEquals(List.of("bind"), underTest.analyze("bind-").getInputTerms());
		assertEquals(List.of("under"), underTest.analyze("under_").getInputTerms());
	}

	@Test
	public void testSingleBindCharactersAreRemoved() {
		assertEquals(Arrays.asList("hochvolt", "schiene"), underTest.analyze("hochvolt - schiene").getInputTerms());
	}

	@Test
	public void testQuotesAreRemoved() {
		assertEquals(Arrays.asList("monitor", "15.6"), underTest.analyze("monitor 15.6\"").getInputTerms());
		assertEquals(Arrays.asList("model", "superduper"), underTest.analyze("model \"superduper\"").getInputTerms());
	}

	@Test
	public void testAsianCharacters() {
		assertEquals(List.of("牌信誉"), underTest.analyze("牌信誉").getInputTerms());
	}

	@Test
	public void testDimensionsSeparatorX() {
		assertEquals(List.of("12x34"), underTest.analyze("12x34").getInputTerms());
	}

	@Test
	public void testDimensionsSeparatorAstrx() {
		assertEquals(Arrays.asList("12", "34"), underTest.analyze("12*34").getInputTerms());
	}
}
