package de.cxp.ocs.elasticsearch.query.analyzer;

import static de.cxp.ocs.elasticsearch.model.query.QueryBoosting.BoostType.DOWN;
import static de.cxp.ocs.elasticsearch.model.query.QueryBoosting.BoostType.UP;
import static de.cxp.ocs.elasticsearch.query.analyzer.AnalyzerUtil.extractTerms;
import static de.cxp.ocs.util.TestUtils.assertAndCastInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.query.QueryBoosting;
import de.cxp.ocs.elasticsearch.model.term.*;
import de.cxp.ocs.elasticsearch.model.util.QueryStringUtil;
import de.cxp.ocs.elasticsearch.query.analyzer.QuerqyQueryExpanderBuilder.RuleLoadingFlags;
import lombok.extern.slf4j.Slf4j;

/**
 * A test that indirectly also tests QuerqyQueryExpander.
 */
@Slf4j
public class QuerqyQueryExpanderTest {

	private QuerqyQueryExpanderBuilder qqBuilder = new QuerqyQueryExpanderBuilder();

	@AfterEach
	public void cleanupTempRuleFile() {
		qqBuilder.createdTempFiles.forEach(file -> {
			try {
				file.delete();
			}
			catch (Exception e) {
				log.info("failed to delete file {}", file, e);
			}
		});
	}


	@Test
	public void testAsciifiedRule() {
		// rules are asciified
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules(EnumSet.of(RuleLoadingFlags.ASCIIFY), "dziecięce =>", "  SYNONYM(0.5): dziewczęce");
		// so that a ascii input triggers them
		var analyzedQuery = analyze(underTest, "dzieciece");
		assertEquals("(dzieciece OR dziewczece^0.5)", analyzedQuery.toQueryString());
	}

	@Test
	public void testAsciifiedAndLowercasedRules() {
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules(EnumSet.of(RuleLoadingFlags.ASCIIFY, RuleLoadingFlags.LOWERCASE), "Dzięci =>", "  SYNONYM(0.5): Dziewczęce");
		ExtendedQuery analyzedQuery = analyze(underTest, "dzieci");
		assertEquals("(dzieci OR dziewczece^0.5)", analyzedQuery.toQueryString());
	}

	@Test
	public void testAsciifiedAndLowercasedRulesAndInput() {
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules(EnumSet.of(RuleLoadingFlags.ASCIIFY, RuleLoadingFlags.LOWERCASE), "Dzięci =>", "  SYNONYM(0.5): Dziewczęce");
		ExtendedQuery analyzedQuery = analyze(underTest, "dzięci");
		assertEquals("(dzięci OR dziewczece^0.5)", analyzedQuery.toQueryString());
	}

	@Test
	public void testLowercasingRules() {
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules(EnumSet.of(RuleLoadingFlags.LOWERCASE), "Kreslo =>", "  SYNONYM(0.82): POLSTER");

		ExtendedQuery analyzedQuery2 = analyze(underTest, "kreslo");
		assertEquals("(kreslo OR polster^0.82)", analyzedQuery2.toQueryString());
	}

	@Test
	public void testLowercasingRulesAndInput() {
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules(EnumSet.of(RuleLoadingFlags.LOWERCASE), "Kreslo =>", "  SYNONYM(0.82): POLSTER");

		ExtendedQuery analyzedQuery2 = analyze(underTest, "KRESLO");
		assertEquals("(KRESLO OR polster^0.82)", analyzedQuery2.toQueryString());
	}

	@Test
	public void testNonWordInputQuery() {
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  SYNONYM: synonym");
		var analyzedQuery = analyze(underTest, "<");
		assertTrue(analyzedQuery.isEmpty());

		analyzedQuery = analyze(underTest, "< input > input2<");
		assertFalse(analyzedQuery.isEmpty());
		assertEquals("(input OR synonym) input2", analyzedQuery.toQueryString());
	}

	@Test
	public void testOneToManySynonym() {
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("surpresseur =>", "  SYNONYM: (pompe à immerger)");
		var analyzedQuery = analyze(underTest, "surpresseur");
		assertEquals("(surpresseur OR \"pompe à immerger\")", analyzedQuery.toQueryString());
	}

	@Test
	public void testTwoSynonymsInOneRule() {
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  SYNONYM: word1 word2");
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
	public void testDefaultBoostingWeight() {
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>"
				, "  UP: * field:word1"
				, "  DOWN: word2");

		ExtendedQuery analyzedQuery = analyze(underTest, "input more");

		assertEquals(2, analyzedQuery.getBoostings().size());
		List<QueryBoosting> expectedBoostings = List.of(
				new QueryBoosting("field", "word1", UP, 1),
				new QueryBoosting(null, "word2", DOWN, 1));

		expectedBoostings.forEach(boosting -> assertTrue(analyzedQuery.getBoostings().contains(boosting)));
	}

	@Test
	public void testSingleFieldBoosting() {
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  UP(10): * field:word1");
		var analyzedQuery = analyze(underTest, "input more");

		assertEquals(1, analyzedQuery.getBoostings().size());
		QueryBoosting expected = new QueryBoosting("field", "word1", UP, 10);
		assertEquals(expected, analyzedQuery.getBoostings().get(0));

		underTest = qqBuilder.loadWithRules("input =>", "  DOWN(11): * field:word1");
		analyzedQuery = analyze(underTest, "input more");

		assertEquals(1, analyzedQuery.getBoostings().size());
		expected = new QueryBoosting("field", "word1", DOWN, 11);
		assertEquals(expected, analyzedQuery.getBoostings().get(0));
	}

	@Test
	public void testSingleQueryBoosting() {
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  UP(10): word1");
		ExtendedQuery analyzedQuery = analyze(underTest, "input more");

		assertEquals(1, analyzedQuery.getBoostings().size());
		QueryBoosting expected = new QueryBoosting(null, "word1", UP, 10);
		assertEquals(expected, analyzedQuery.getBoostings().get(0));

		underTest = qqBuilder.loadWithRules("input =>", "  DOWN(11): word1");
		analyzedQuery = analyze(underTest, "input more");

		assertEquals(1, analyzedQuery.getBoostings().size());
		expected = new QueryBoosting(null, "word1", DOWN, 11);
		assertEquals(expected, analyzedQuery.getBoostings().get(0));
	}

	@Test
	public void testMultiQueryFieldBoosting() {
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>"
				, "  UP(10): * field1:word1"
				, "  DOWN(11): * field2:word2"
				, "  UP(10): word1"
				, "  DOWN(11): word2");
		ExtendedQuery analyzedQuery = analyze(underTest, "input more");

		assertEquals(4, analyzedQuery.getBoostings().size());
		List<QueryBoosting> expectedBoostings = List.of(
				new QueryBoosting("field1", "word1", UP, 10),
				new QueryBoosting("field2", "word2", DOWN, 11),
				new QueryBoosting(null, "word1", UP, 10),
				new QueryBoosting(null, "word2", DOWN, 11));

		expectedBoostings.forEach(boosting -> assertTrue(analyzedQuery.getBoostings().contains(boosting)));
	}

	@Test
	public void testTwoSynonymsInTwoRule() {
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  SYNONYM: word1", "  SYNONYM: word2");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  FILTER: -remove");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  FILTER: (everything match together)");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  FILTER: -remove +must should^0.8 fuzzy~");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  FILTER: * -(a whole phrase)");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  FILTER: * -\"phrase to exclude\"");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  FILTER: * -field1:remove");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  FILTER: * -field1:removeA field2:removeB");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  FILTER: * -field:removeA -field:removeB");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  FILTER: * -category.id:111222000");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  FILTER: * -field2:\"foo bar\"");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  FILTER: * -(my ventilátor) -brand:puma");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  FILTER: * -field2:foo bar");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules(
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules(
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  FILTER: * -catPath:Cat1/Sub Category/a, b & c");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  FILTER: * -catPath:Cat1/Sub Category/a, b & c -catPath:Cat2/Sub Cat/x, y & z");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  FILTER: * -catPath:Cat1/Sub Category/a, b & c brand:\"Awesome Stuff\" price:12-100.23");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("input =>", "  SYNONYM(0.2): (two words)");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules("fer à repasser =>", "  SYNONYM(0.7): centrale vapeur");
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules(
				"e bike =>",
				"  SYNONYM: pedelec",
				"bike shirt =>",
				"  SYNONYM: trikot");
		var analyzedQuery = analyze(underTest, "e bike shirt");
		assertEquals("(e OR pedelec) (bike OR pedelec OR trikot) (shirt OR trikot)", analyzedQuery.getSearchQuery().toQueryString());
	}

	@Test
	public void testMultipleSynonymsForMultiTermInput() {
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules(
				"fer à repasser =>",
				"  SYNONYM: centrale vapeur",
				"fer =>",
				"  SYNONYM: metal");
		var analyzedQuery = analyze(underTest, "fer à repasser philips");
		assertEquals("(((fer OR metal) à repasser) OR \"centrale vapeur\") philips", analyzedQuery.getSearchQuery().toQueryString());
	}

	@Test
	public void testMultipleMultiTermSynonymsForSingleTermInput() {
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules(
				"in1 =>",
				"  SYNONYM: out1",
				"  SYNONYM: out2 out3");
		var analyzedQuery = analyze(underTest, "in1 in2");
		assertEquals("((in1 OR out1) OR \"out2 out3\") in2", analyzedQuery.getSearchQuery().toQueryString());
	}

	@Test
	public void testMultipleMultiTermSynonymsForMultiTermInput() {
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules(
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
		QuerqyQueryExpander underTest = qqBuilder.loadWithRules(
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
