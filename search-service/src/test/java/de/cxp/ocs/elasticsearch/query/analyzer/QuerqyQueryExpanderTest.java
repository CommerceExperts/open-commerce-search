package de.cxp.ocs.elasticsearch.query.analyzer;

import static de.cxp.ocs.elasticsearch.query.analyzer.AnalyzerUtil.extractTerms;
import static de.cxp.ocs.util.TestUtils.assertAndCastInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.apache.lucene.search.BooleanClause.Occur;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.term.*;
import de.cxp.ocs.elasticsearch.model.util.QueryStringUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * A test that indirectly also tests QuerqyQueryExpander.
 */
@Slf4j
public class QuerqyQueryExpanderTest {

	private File						deleteAfterTest;

	@AfterEach
	public void cleanupTempRuleFile() {
		if (deleteAfterTest != null) {
			try {
				deleteAfterTest.delete();
			}
			catch (Exception e) {
				// ignore
			}
			deleteAfterTest = null;
		}
	}

	public QuerqyQueryExpander loadRule(String... instructions) {
		return this.loadRule(EnumSet.noneOf(RuleLoadingFlags.class), instructions);
	}

	enum RuleLoadingFlags {
		ASCIIFY, LOWERCASE
	}

	public QuerqyQueryExpander loadRule(EnumSet<RuleLoadingFlags> loadingFlags, String... instructions) {
		QuerqyQueryExpander underTest = new QuerqyQueryExpander();
		Path querqyRulesFile;
		try {
			querqyRulesFile = Files.createTempFile("querqy_rules_test.", ".txt");
			Files.write(querqyRulesFile, Arrays.asList(instructions), StandardCharsets.UTF_8);

			Map<String, String> options = new HashMap<>();
			options.put(QuerqyQueryExpander.RULES_URL_PROPERTY_NAME, querqyRulesFile.toAbsolutePath().toString());
			options.put(QuerqyQueryExpander.DO_ASCIIFY_RULES_PROPERTY_NAME, Boolean.toString(loadingFlags.contains(RuleLoadingFlags.ASCIIFY)));
			options.put(QuerqyQueryExpander.DO_LOWERCASE_RULES_PROPERTY_NAME, Boolean.toString(loadingFlags.contains(RuleLoadingFlags.LOWERCASE)));
			underTest.initialize(options);
			deleteAfterTest = querqyRulesFile.toFile();
		}
		catch (IOException e) {
			throw new TestAbortedException("could not write querqy rules file", e);
		}
		return underTest;
	}

	@Test
	public void testAsciifiedRule() {
		// rules are asciified
		QuerqyQueryExpander underTest = loadRule(EnumSet.of(RuleLoadingFlags.ASCIIFY), "dziecięce =>", "  SYNONYM(0.5): dziewczęce");
		// so that a ascii input triggers them
		var analyzedQuery = analyze(underTest, "dzieciece");
		assertEquals("(dzieciece OR dziewczece^0.5)", analyzedQuery.toQueryString());
	}

	@Test
	public void testAsciifiedAndLowercasedRules() {
		QuerqyQueryExpander underTest = loadRule(EnumSet.of(RuleLoadingFlags.ASCIIFY, RuleLoadingFlags.LOWERCASE), "Dzięci =>", "  SYNONYM(0.5): Dziewczęce");
		ExtendedQuery analyzedQuery = analyze(underTest, "dzieci");
		assertEquals("(dzieci OR dziewczece^0.5)", analyzedQuery.toQueryString());
	}

	@Test
	public void testAsciifiedAndLowercasedRulesAndInput() {
		QuerqyQueryExpander underTest = loadRule(EnumSet.of(RuleLoadingFlags.ASCIIFY, RuleLoadingFlags.LOWERCASE), "Dzięci =>", "  SYNONYM(0.5): Dziewczęce");
		ExtendedQuery analyzedQuery = analyze(underTest, "Dzięci");
		assertEquals("(dzieci OR dziewczece^0.5)", analyzedQuery.toQueryString());
	}

	public void testLowercasingRules() {
		QuerqyQueryExpander underTest = loadRule(EnumSet.of(RuleLoadingFlags.LOWERCASE), "Kreslo =>", "  SYNONYM(0.82): POLSTER");

		ExtendedQuery analyzedQuery2 = analyze(underTest, "kreslo");
		assertEquals("(kreslo OR polster^0.82)", analyzedQuery2.toQueryString());
	}

	public void testLowercasingRulesAndInput() {
		QuerqyQueryExpander underTest = loadRule(EnumSet.of(RuleLoadingFlags.LOWERCASE), "Kreslo =>", "  SYNONYM(0.82): POLSTER");

		ExtendedQuery analyzedQuery2 = analyze(underTest, "KRESLOl");
		assertEquals("(kreslo OR polster^0.82)", analyzedQuery2.toQueryString());
	}

	@Test
	public void testNonWordInputQuery() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  SYNONYM: synonym");
		var analyzedQuery = analyze(underTest, "<");
		assertTrue(analyzedQuery.isEmpty());

		analyzedQuery = analyze(underTest, "< input > input2<");
		assertFalse(analyzedQuery.isEmpty());
		assertEquals("(input OR synonym) input2", analyzedQuery.toQueryString());
	}

	@Test
	public void testTwoSynonymsInOneRule() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  SYNONYM: word1 word2");
		var analyzedQuery = analyze(underTest, "input more");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(2, result.size());
		AssociatedTerm term = assertAndCastInstanceOf(result.get(0), AssociatedTerm.class);
		assertEquals("input", term.getRawTerm());

		assertEquals(1, term.getRelatedTerms().size(), term.getRelatedTerms()::toString);
		assertTrue(term.getRelatedTerms().keySet().contains("word1 word2"));

		WeightedTerm relatedWords = assertAndCastInstanceOf(term.getRelatedTerms().get("word1 word2"), WeightedTerm.class);
		assertEquals(new WeightedTerm("word1 word2", 1f, false, true, Occur.SHOULD), relatedWords);

		// expect multi-term synonym in quotes to consider them as a phrase
		assertEquals("(input OR \"word1 word2\") more", QueryStringUtil.buildQueryString(result, " "));
	}

	@Test
	public void testTwoSynonymsInTwoRule() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  SYNONYM: word1", "  SYNONYM: word2");
		var analyzedQuery = analyze(underTest, "input more");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(2, result.size(), result::toString);
		AssociatedTerm term1 = assertAndCastInstanceOf(result.get(0), AssociatedTerm.class);
		assertEquals("input", term1.getRawTerm());

		WeightedTerm term2 = assertAndCastInstanceOf(result.get(1), WeightedTerm.class);
		assertEquals("more", term2.getRawTerm());

		assertEquals(2, term1.getRelatedTerms().size(), term1.getRelatedTerms()::toString);
		assertTrue(term1.getRelatedTerms().keySet().contains("word1"));
		assertTrue(term1.getRelatedTerms().keySet().contains("word2"));

		WeightedTerm relatedWord1 = assertAndCastInstanceOf(term1.getRelatedTerms().get("word1"), WeightedTerm.class);
		assertEquals(new WeightedTerm("word1", 1f, Occur.SHOULD), relatedWord1);

		WeightedTerm relatedWord2 = assertAndCastInstanceOf(term1.getRelatedTerms().get("word2"), WeightedTerm.class);
		assertEquals(new WeightedTerm("word2", 1f, Occur.SHOULD), relatedWord2);

		// unfortunately the order of the terms is not very predictable
		assertEquals("(input OR word2 OR word1) more", analyzedQuery.getSearchQuery().toQueryString());
	}

	@Test
	public void testExcludeQueryTerm() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: -remove");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(2, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		WeightedTerm term2 = assertAndCastInstanceOf(result.get(1), WeightedTerm.class);
		assertEquals("remove", term2.getRawTerm());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}

	public void testCombinedTermFilter() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: (everything match together)");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(2, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		WeightedTerm term2 = assertAndCastInstanceOf(result.get(1), WeightedTerm.class);
		assertEquals("everything match together", term2.getRawTerm());
		assertTrue(term2.isQuoted());
		assertEquals(Occur.SHOULD, term2.getOccur());
	}

	@Disabled("TODO: not supported yet, because simplified implementation only done for combined term in brackets")
	@Test
	public void testMultipleTermFilter() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: -remove +must should^0.8 fuzzy~");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(5, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		WeightedTerm term2 = assertAndCastInstanceOf(result.get(1), WeightedTerm.class);
		assertEquals("remove", term2.getRawTerm());
		assertEquals(Occur.MUST_NOT, term2.getOccur());

		WeightedTerm term3 = assertAndCastInstanceOf(result.get(2), WeightedTerm.class);
		assertEquals("must", term3.getRawTerm());
		assertEquals(Occur.MUST, term3.getOccur());

		WeightedTerm term4 = assertAndCastInstanceOf(result.get(3), WeightedTerm.class);
		assertEquals("should", term4.getRawTerm());
		assertEquals(0.8f, term4.getWeight());
		assertEquals(Occur.SHOULD, term4.getOccur());

		WeightedTerm term5 = assertAndCastInstanceOf(result.get(3), WeightedTerm.class);
		assertEquals("fuzzy", term5.getRawTerm());
		assertTrue(term5.isFuzzy());
		assertEquals(Occur.SHOULD, term5.getOccur());
	}

	@Test
	public void testExcludeMultiTermFilter() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -(a whole phrase)");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(2, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		RawTerm term2 = assertAndCastInstanceOf(result.get(1), RawTerm.class);
		assertEquals("-(a whole phrase)", term2.toString());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}

	@Test
	public void testExcludeQueryPhrase() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -\"phrase to exclude\"");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(2, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		RawTerm term2 = assertAndCastInstanceOf(result.get(1), RawTerm.class);
		assertEquals("-\"phrase to exclude\"", term2.toQueryString());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}

	@Test
	public void testExcludeFieldFilter() {
		// field filters have to be specified as raw queries (filter instruction starting with the *)
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -field1:remove");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(2, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("field1", term2.getField());
		assertEquals("remove", term2.getRawTerm());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}

	@Test
	public void testTwoFieldFilter() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -field1:removeA field2:removeB");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(3, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("field1", term2.getField());
		assertEquals("removeA", term2.getRawTerm());
		assertEquals(Occur.MUST_NOT, term2.getOccur());

		QueryFilterTerm term3 = assertAndCastInstanceOf(result.get(2), QueryFilterTerm.class);
		assertEquals("field2", term3.getField());
		assertEquals("removeB", term3.getRawTerm());
		assertEquals(Occur.MUST, term3.getOccur());
	}

	@Test
	public void testSameFieldTwoFilter() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -field:removeA -field:removeB");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(3, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("field", term2.getField());
		assertEquals("removeA", term2.getRawTerm());
		assertEquals(Occur.MUST_NOT, term2.getOccur());

		QueryFilterTerm term3 = assertAndCastInstanceOf(result.get(2), QueryFilterTerm.class);
		assertEquals("field", term3.getField());
		assertEquals("removeB", term3.getRawTerm());
		assertEquals(Occur.MUST_NOT, term3.getOccur());
	}

	@Test
	public void testExcludeFieldIDFilter() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -category.id:111222000");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(2, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("category.id", term2.getField());
		assertEquals("111222000", term2.getRawTerm());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}


	@Test
	public void testValidExcludeFieldMultiWordFilter() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -field2:\"foo bar\"");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(2, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("field2", term2.getField());
		assertEquals("foo bar", term2.getRawTerm());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}

	@Test
	public void testCombinedRawFilter() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -(my ventilátor) -brand:puma");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);
		assertEquals(3, result.size(), result::toString);

		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm(), result::toString);

		RawTerm term2 = assertAndCastInstanceOf(result.get(2), RawTerm.class);
		assertEquals("-(my ventilátor)", term2.toString(), result::toString);

		QueryFilterTerm term3 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("brand", term3.getField());
		assertEquals("puma", term3.getRawTerm());
		assertEquals(Occur.MUST_NOT, term3.getOccur());
	}

	@Test
	public void testInvalidExcludeFieldMultiWordFilter() {
		// actually this is invalid lucene syntax, but in this context it's the only thing that makes sense
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -field2:foo bar");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(2, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("field2", term2.getField());
		assertEquals("foo bar", term2.getRawTerm());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}

	@Test
	public void testReservedCharsInFilterClause() {
		// field filters MUST be passed as raw queries, but if part of a term rule,
		// colons and all reserved chars are considered as the term-content and are escaped
		QuerqyQueryExpander underTest = loadRule(
				"input =>",
				"  FILTER: -excl_term -field3:filter -(foo/bar)");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(4, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		WeightedTerm term2 = assertAndCastInstanceOf(result.get(1), WeightedTerm.class);
		assertEquals("excl_term", term2.getRawTerm());
		assertEquals(Occur.MUST_NOT, term2.getOccur());

		WeightedTerm term3 = assertAndCastInstanceOf(result.get(2), WeightedTerm.class);
		assertEquals("-\"field3\\:filter\"", term3.toQueryString());
		assertEquals(Occur.MUST_NOT, term3.getOccur());

		WeightedTerm term4 = assertAndCastInstanceOf(result.get(3), WeightedTerm.class);
		assertEquals("-\"\\(foo\\/bar\\)\"", term4.toQueryString());
		assertEquals(Occur.MUST_NOT, term4.getOccur());
	}

	@Test
	public void testReservedCharsInRawFilterClause() {
		// field filters MUST be passed as raw queries, but if part of a term rule,
		// colons and all reserved chars are considered as the term-content and are escaped
		QuerqyQueryExpander underTest = loadRule(
				"input =>",
				"  FILTER: * -(foo/bar)");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(2, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		RawTerm term2 = assertAndCastInstanceOf(result.get(1), RawTerm.class);
		assertEquals("-(foo/bar)", term2.toQueryString());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}

	@Test
	public void testExcludeCategoryPathFilter() {
		// actually this is invalid lucene syntax, but in this context it's the only thing that makes sense
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -catPath:Cat1/Sub Category/a, b & c");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(2, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("catPath", term2.getField());
		assertEquals("Cat1/Sub Category/a, b & c", term2.getRawTerm());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}

	@Test
	public void testExcludeMultipleCategoryPathFilters() {
		// actually this is invalid lucene syntax, but in this context it's the only thing that makes sense
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -catPath:Cat1/Sub Category/a, b & c -catPath:Cat2/Sub Cat/x, y & z");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(3, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("catPath", term2.getField());
		assertEquals("Cat1/Sub Category/a, b & c", term2.getRawTerm());
		assertEquals(Occur.MUST_NOT, term2.getOccur());

		QueryFilterTerm term3 = assertAndCastInstanceOf(result.get(2), QueryFilterTerm.class);
		assertEquals("catPath", term3.getField());
		assertEquals("Cat2/Sub Cat/x, y & z", term3.getRawTerm());
		assertEquals(Occur.MUST_NOT, term3.getOccur());
	}

	@Test
	public void testInsaneMultipleFilters() {
		// actually this is invalid lucene syntax, but in this context it's the only thing that makes sense
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -catPath:Cat1/Sub Category/a, b & c brand:\"Awesome Stuff\" price:12-100.23");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(4, result.size(), result::toString);
		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("catPath", term2.getField());
		assertEquals("Cat1/Sub Category/a, b & c", term2.getRawTerm());
		assertEquals(Occur.MUST_NOT, term2.getOccur());

		QueryFilterTerm term3 = assertAndCastInstanceOf(result.get(2), QueryFilterTerm.class);
		assertEquals("brand", term3.getField());
		assertEquals("Awesome Stuff", term3.getRawTerm());
		assertEquals(Occur.MUST, term3.getOccur());

		QueryFilterTerm term4 = assertAndCastInstanceOf(result.get(3), QueryFilterTerm.class);
		assertEquals("price", term4.getField());
		assertEquals("12-100.23", term4.getRawTerm());
		assertEquals(Occur.MUST, term4.getOccur());
	}

	/**
	 * for some time we used brackets to ensure two words are used together.
	 * so make sure this still works but with the brackets ignored.
	 */
	@Test
	public void testMultiTermSynonymInBrackets() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  SYNONYM(0.2): (two words)");
		var analyzedQuery = analyze(underTest, "input");
		List<QueryStringTerm> result = extractTerms(analyzedQuery);

		assertEquals(1, result.size());
		AssociatedTerm term = assertAndCastInstanceOf(result.get(0), AssociatedTerm.class);
		assertEquals("input", term.getRawTerm());

		assertEquals(1, term.getRelatedTerms().size(), term.getRelatedTerms()::toString);
		assertTrue(term.getRelatedTerms().keySet().contains("two words"));

		WeightedTerm relatedPhrase = assertAndCastInstanceOf(term.getRelatedTerms().get("two words"), WeightedTerm.class);
		assertEquals(new WeightedTerm("two words", 0.2f, false, true, Occur.SHOULD), relatedPhrase);
		assertEquals("(input OR \"two words\"^0.2)", analyzedQuery.getSearchQuery().toQueryString());
	}

	@Test
	public void testMultiTermSynonymForMultiTermInput() {
		QuerqyQueryExpander underTest = loadRule("fer à repasser =>", "  SYNONYM(0.7): centrale vapeur");
		var analyzedQuery = analyze(underTest, "fer à repasser philips");

		List<QueryStringTerm> result = extractTerms(analyzedQuery);
		assertEquals(2, result.size(), result::toString);

		AssociatedTerm term = assertAndCastInstanceOf(result.get(0), AssociatedTerm.class);
		assertEquals("fer à repasser", term.getMainTerm().getRawTerm());
		assertEquals("centrale vapeur", term.getRelatedTerms().get("centrale vapeur").getRawTerm());

		assertEquals("((fer à repasser) OR \"centrale vapeur\"^0.7) philips", analyzedQuery.getSearchQuery().toQueryString());
	}

	@Test
	public void testOverlappingTermSynonymsForMultiTermInput() {
		QuerqyQueryExpander underTest = loadRule(
				"e bike =>",
				"  SYNONYM: pedelec",
				"bike shirt =>",
				"  SYNONYM: trikot");
		var analyzedQuery = analyze(underTest, "e bike shirt");
		assertEquals("(e OR pedelec) (bike OR pedelec OR trikot) (shirt OR trikot)", analyzedQuery.getSearchQuery().toQueryString());
	}

	@Test
	public void testMultipleSynonymsForMultiTermInput() {
		QuerqyQueryExpander underTest = loadRule(
				"fer à repasser =>",
				"  SYNONYM: centrale vapeur",
				"fer =>",
				"  SYNONYM: metal");
		var analyzedQuery = analyze(underTest, "fer à repasser philips");
		assertEquals("(((fer OR metal) à repasser) OR \"centrale vapeur\") philips", analyzedQuery.getSearchQuery().toQueryString());
	}

	@Test
	public void testMultipleMultiTermSynonymsForSingleTermInput() {
		QuerqyQueryExpander underTest = loadRule(
				"in1 =>",
				"  SYNONYM: out1",
				"  SYNONYM: out2 out3");
		var analyzedQuery = analyze(underTest, "in1 in2");
		assertEquals("((in1 OR out1) OR \"out2 out3\") in2", analyzedQuery.getSearchQuery().toQueryString());
	}

	@Test
	public void testMultipleMultiTermSynonymsForMultiTermInput() {
		QuerqyQueryExpander underTest = loadRule(
				"in1 in2 =>",
				"  SYNONYM: out1 out2",
				"  SYNONYM: out3 out4");
		var analyzedQuery = analyze(underTest, "in1 in2 in3");
		assertEquals("((in1 in2) OR \"out3 out4\" OR \"out1 out2\") in3", analyzedQuery.getSearchQuery().toQueryString());
	}

	@Test
	public void testOverlappingMultiTermSynonymsForMultiTermInput() {
		// this is such a brain-fuck, that it's hardly recommended to avoid such rules at any cost,
		// but if it's possible, customers will do it
		QuerqyQueryExpander underTest = loadRule(
				"in1 in2 =>",
				"  SYNONYM: out1 out2",
				"in2 in3 =>",
				"  SYNONYM: out2 out3");
		var analyzedQuery = analyze(underTest, "in1 in2 in3");
		assertEquals("(((in1 in2) OR \"out1 out2\") in3) OR (in1 ((in2 in3) OR \"out2 out3\"))", analyzedQuery.getSearchQuery().toQueryString());
	}

	/**
	 * analyze and log results
	 * 
	 * @param query
	 * @return
	 */
	private ExtendedQuery analyze(QuerqyQueryExpander underTest, String query) {
		ExtendedQuery analyzedQuery = underTest.analyze(query);
		log.info("query '{}' returned result {}", query, analyzedQuery);

		return analyzedQuery;
	}
}
