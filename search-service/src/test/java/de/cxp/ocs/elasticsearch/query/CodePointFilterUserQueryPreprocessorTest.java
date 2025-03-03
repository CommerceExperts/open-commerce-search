package de.cxp.ocs.elasticsearch.query;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodePointFilterUserQueryPreprocessorTest {

	private static CodePointFilterUserQueryPreprocessor europeanQueryFilter             = new CodePointFilterUserQueryPreprocessor();
	private static CodePointFilterUserQueryPreprocessor europeanQueryFilterWithCyrillic = new CodePointFilterUserQueryPreprocessor();

	static {
		europeanQueryFilter.initialize(Map.of(
				"code_point_lower_bound", "30",
				"code_point_upper_bound", "687"));

		europeanQueryFilterWithCyrillic.initialize(Map.of(
				"code_point_lower_bound", "30",
				"code_point_upper_bound", "1327"));
	}

	@Test
	public void testBasicLatin() {
		String userQuery = "lorem ipsum";

		String filtered = europeanQueryFilter.preProcess(userQuery);
		assertEquals(userQuery, filtered);

		String filtered2 = europeanQueryFilterWithCyrillic.preProcess(userQuery);
		assertEquals(userQuery, filtered2);
	}

	@Test
	public void testChineseLetterFilteringWithLatin() {
		// does not filter spam addresses
		String filtered = europeanQueryFilter.preProcess("微信实名号批发 [xxx.com]");
		assertEquals(" [xxx.com]", filtered);
	}

	@Test
	public void testChineseLetterFiltering() {
		String filtered = europeanQueryFilter.preProcess("微信实名号批发");
		assertEquals("", filtered);
	}

	@Test
	public void testRussianLetterFiltering() {
		String userQuery = "черные джинсы";
		String filtered = europeanQueryFilter.preProcess(userQuery);
		assertEquals("", filtered.trim());

		// cyrillic filter should not remove characters
		assertEquals(userQuery, europeanQueryFilterWithCyrillic.preProcess(userQuery));
	}

	@Test
	public void testFrench() {
		String userQuery = "boîte à cartes Pokémon";
		String filtered = europeanQueryFilter.preProcess(userQuery);
		assertEquals(userQuery, filtered);
	}

	@Test
	public void testCzech() {
		String userQuery = "lištový systém a stojací lampa";
		String filtered = europeanQueryFilter.preProcess(userQuery);
		assertEquals(userQuery, filtered);
	}

	@Test
	public void testPolish() {
		String userQuery = "lampa podłogowa";
		String filtered = europeanQueryFilter.preProcess(userQuery);
		assertEquals(userQuery, filtered);
	}

	@Test
	public void testPortuguese() {
		String userQuery = "luminária de chão";
		String filtered = europeanQueryFilter.preProcess(userQuery);
		assertEquals(userQuery, filtered);
	}
}
