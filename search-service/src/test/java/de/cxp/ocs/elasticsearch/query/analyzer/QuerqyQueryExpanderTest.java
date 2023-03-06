package de.cxp.ocs.elasticsearch.query.analyzer;

import static de.cxp.ocs.util.TestUtils.assertAndCastInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.search.BooleanClause.Occur;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import de.cxp.ocs.elasticsearch.query.model.*;
import de.cxp.ocs.util.ESQueryUtils;
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
		QuerqyQueryExpander underTest = new QuerqyQueryExpander();
		Path querqyRulesFile;
		try {
			querqyRulesFile = Files.createTempFile("querqy_rules_test.", ".txt");
			Files.write(querqyRulesFile, Arrays.asList(instructions), StandardCharsets.UTF_8);
			underTest.initialize(Collections.singletonMap("common_rules_url", querqyRulesFile.toAbsolutePath().toString()));
			deleteAfterTest = querqyRulesFile.toFile();
		}
		catch (IOException e) {
			throw new TestAbortedException("could not write querqy rules file", e);
		}
		return underTest;
	}

	@Test
	public void testTwoSynonymsInOneRule() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  SYNONYM: word1 word2");
		List<QueryStringTerm> result = analyze(underTest, "input more");
		assertEquals(2, result.size());
		WordAssociation term = assertAndCastInstanceOf(result.get(0), WordAssociation.class);
		assertEquals("input", term.getOriginalWord());

		assertEquals(1, term.getRelatedWords().size(), term.getRelatedWords()::toString);
		assertTrue(term.getRelatedWords().keySet().contains("word1 word2"));

		WeightedWord relatedWords = assertAndCastInstanceOf(term.getRelatedWords().get("word1 word2"), WeightedWord.class);
		assertEquals(new WeightedWord("word1 word2", 1f, false, true, Occur.SHOULD), relatedWords);

		// expect multi-term synonym in quotes to consider them as a phrase
		assertEquals("(input OR \"word1 word2\") more", ESQueryUtils.buildQueryString(result, " "));
	}

	@Test
	public void testTwoSynonymsInTwoRule() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  SYNONYM: word1", "  SYNONYM: word2");
		List<QueryStringTerm> result = analyze(underTest, "input more");
		assertEquals(2, result.size(), result::toString);
		WordAssociation term1 = assertAndCastInstanceOf(result.get(0), WordAssociation.class);
		assertEquals("input", term1.getOriginalWord());

		WeightedWord term2 = assertAndCastInstanceOf(result.get(1), WeightedWord.class);
		assertEquals("more", term2.getWord());

		assertEquals(2, term1.getRelatedWords().size(), term1.getRelatedWords()::toString);
		assertTrue(term1.getRelatedWords().keySet().contains("word1"));
		assertTrue(term1.getRelatedWords().keySet().contains("word2"));

		WeightedWord relatedWord1 = assertAndCastInstanceOf(term1.getRelatedWords().get("word1"), WeightedWord.class);
		assertEquals(new WeightedWord("word1", 1f, Occur.SHOULD), relatedWord1);

		WeightedWord relatedWord2 = assertAndCastInstanceOf(term1.getRelatedWords().get("word2"), WeightedWord.class);
		assertEquals(new WeightedWord("word2", 1f, Occur.SHOULD), relatedWord2);

		// unfortunately the order of the terms is not very predictable
		assertEquals("(input OR word2 OR word1) more", ESQueryUtils.buildQueryString(result, " "));
	}

	/**
	 * for some time we used brackets to ensure two words are used together.
	 * so make sure this still works but with the brackets ignored.
	 */
	@Test
	public void testMultiTermSynonymInBrackets() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  SYNONYM(0.2): (two words)");
		List<QueryStringTerm> result = analyze(underTest, "input");
		assertEquals(1, result.size());
		WordAssociation term = assertAndCastInstanceOf(result.get(0), WordAssociation.class);
		assertEquals("input", term.getOriginalWord());

		assertEquals(1, term.getRelatedWords().size(), term.getRelatedWords()::toString);
		assertTrue(term.getRelatedWords().keySet().contains("two words"));

		WeightedWord relatedPhrase = assertAndCastInstanceOf(term.getRelatedWords().get("two words"), WeightedWord.class);
		assertEquals(new WeightedWord("two words", 0.2f, false, true, Occur.SHOULD), relatedPhrase);
		assertEquals("(input OR \"two words\"^0.2)", ESQueryUtils.buildQueryString(result, " "));
	}

	@Test
	public void testMultiTermSynonymForMultiTermInput() {
		QuerqyQueryExpander underTest = loadRule("fer à repasser =>", "  SYNONYM(0.7): centrale vapeur");
		List<QueryStringTerm> result = analyze(underTest, "fer à repasser philips");
		assertEquals(2, result.size(), result::toString);

		AlternativeTerm term = assertAndCastInstanceOf(result.get(0), AlternativeTerm.class);
		assertEquals("fer à repasser", term.getAlternatives().get(0).getWord());
		assertEquals("centrale vapeur", term.getAlternatives().get(1).getWord());

		assertEquals("((fer à repasser) OR \"centrale vapeur\"^0.7) philips", ESQueryUtils.buildQueryString(result, " "));
	}

	@Test
	public void testMultipleSynonymsForMultiTermInput() {
		QuerqyQueryExpander underTest = loadRule(
				"fer à repasser =>",
				"  SYNONYM: centrale vapeur",
				"fer =>",
				"  SYNONYM: metal");
		List<QueryStringTerm> result = analyze(underTest, "fer à repasser philips");
		assertEquals("(((fer OR metal) à repasser) OR \"centrale vapeur\") philips", ESQueryUtils.buildQueryString(result, " "));
	}

	@Test
	public void testExcludeQueryTerm() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: -remove");
		List<QueryStringTerm> result = analyze(underTest, "input");

		assertEquals(2, result.size(), () -> result.toString());
		WeightedWord term1 = assertAndCastInstanceOf(result.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		WeightedWord term2 = assertAndCastInstanceOf(result.get(1), WeightedWord.class);
		assertEquals("remove", term2.getWord());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}

	public void testCombinedTermFilter() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: (everything match together)");
		List<QueryStringTerm> result = analyze(underTest, "input");

		assertEquals(2, result.size(), () -> result.toString());
		WeightedWord term1 = assertAndCastInstanceOf(result.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		WeightedWord term2 = assertAndCastInstanceOf(result.get(1), WeightedWord.class);
		assertEquals("everything match together", term2.getWord());
		assertTrue(term2.isQuoted());
		assertEquals(Occur.SHOULD, term2.getOccur());
	}

	@Disabled("TODO: not supported yet, because simplified implementation only done for combined term in brackets")
	@Test
	public void testMultipleTermFilter() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: -remove +must should^0.8 fuzzy~");
		List<QueryStringTerm> result = analyze(underTest, "input");

		assertEquals(5, result.size(), () -> result.toString());
		WeightedWord term1 = assertAndCastInstanceOf(result.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		WeightedWord term2 = assertAndCastInstanceOf(result.get(1), WeightedWord.class);
		assertEquals("remove", term2.getWord());
		assertEquals(Occur.MUST_NOT, term2.getOccur());

		WeightedWord term3 = assertAndCastInstanceOf(result.get(2), WeightedWord.class);
		assertEquals("must", term3.getWord());
		assertEquals(Occur.MUST, term3.getOccur());

		WeightedWord term4 = assertAndCastInstanceOf(result.get(3), WeightedWord.class);
		assertEquals("should", term4.getWord());
		assertEquals(0.8f, term4.getWeight());
		assertEquals(Occur.SHOULD, term4.getOccur());

		WeightedWord term5 = assertAndCastInstanceOf(result.get(3), WeightedWord.class);
		assertEquals("fuzzy", term5.getWord());
		assertTrue(term5.isFuzzy());
		assertEquals(Occur.SHOULD, term5.getOccur());
	}

	@Test
	public void testExcludeMultiTermFilter() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -(a whole phrase)");
		List<QueryStringTerm> result = analyze(underTest, "input");

		assertEquals(2, result.size(), () -> result.toString());
		WeightedWord term1 = assertAndCastInstanceOf(result.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		RawQueryString term2 = assertAndCastInstanceOf(result.get(1), RawQueryString.class);
		assertEquals("-(a whole phrase)", term2.toString());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}

	@Test
	public void testExcludeQueryPhrase() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -\"phrase to exclude\"");
		List<QueryStringTerm> result = analyze(underTest, "input");

		assertEquals(2, result.size(), () -> result.toString());
		WeightedWord term1 = assertAndCastInstanceOf(result.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		RawQueryString term2 = assertAndCastInstanceOf(result.get(1), RawQueryString.class);
		assertEquals("-\"phrase to exclude\"", term2.toQueryString());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}

	@Test
	public void testExcludeFieldFilter() {
		// field filters have to be specified as raw queries (filter instruction starting with the *)
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -field1:remove");
		List<QueryStringTerm> result = analyze(underTest, "input");

		assertEquals(2, result.size(), () -> result.toString());
		WeightedWord term1 = assertAndCastInstanceOf(result.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("field1", term2.getField());
		assertEquals("remove", term2.getWord());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}

	@Test
	public void testTwoFieldFilter() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -field1:removeA field2:removeB");
		List<QueryStringTerm> result = analyze(underTest, "input");

		assertEquals(3, result.size(), () -> result.toString());
		WeightedWord term1 = assertAndCastInstanceOf(result.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("field1", term2.getField());
		assertEquals("removeA", term2.getWord());
		assertEquals(Occur.MUST_NOT, term2.getOccur());

		QueryFilterTerm term3 = assertAndCastInstanceOf(result.get(2), QueryFilterTerm.class);
		assertEquals("field2", term3.getField());
		assertEquals("removeB", term3.getWord());
		assertEquals(Occur.MUST, term3.getOccur());
	}

	@Test
	public void testSameFieldTwoFilter() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -field:removeA -field:removeB");
		List<QueryStringTerm> result = analyze(underTest, "input");

		assertEquals(3, result.size(), () -> result.toString());
		WeightedWord term1 = assertAndCastInstanceOf(result.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("field", term2.getField());
		assertEquals("removeA", term2.getWord());
		assertEquals(Occur.MUST_NOT, term2.getOccur());

		QueryFilterTerm term3 = assertAndCastInstanceOf(result.get(2), QueryFilterTerm.class);
		assertEquals("field", term3.getField());
		assertEquals("removeB", term3.getWord());
		assertEquals(Occur.MUST_NOT, term3.getOccur());
	}

	@Test
	public void testExcludeFieldIDFilter() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -category.id:111222000");
		List<QueryStringTerm> result = analyze(underTest, "input");

		assertEquals(2, result.size(), () -> result.toString());
		WeightedWord term1 = assertAndCastInstanceOf(result.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("category.id", term2.getField());
		assertEquals("111222000", term2.getWord());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}


	@Test
	public void testValidExcludeFieldMultiWordFilter() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -field2:\"foo bar\"");
		List<QueryStringTerm> result = analyze(underTest, "input");

		assertEquals(2, result.size(), () -> result.toString());
		WeightedWord term1 = assertAndCastInstanceOf(result.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("field2", term2.getField());
		assertEquals("foo bar", term2.getWord());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}

	@Test
	public void testInvalidExcludeFieldMultiWordFilter() {
		// actually this is invalid lucene syntax, but in this context it's the only thing that makes sense
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -field2:foo bar");
		List<QueryStringTerm> result = analyze(underTest, "input");

		assertEquals(2, result.size(), () -> result.toString());
		WeightedWord term1 = assertAndCastInstanceOf(result.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("field2", term2.getField());
		assertEquals("foo bar", term2.getWord());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}

	@Test
	public void testExcludeCategoryPathFilter() {
		// actually this is invalid lucene syntax, but in this context it's the only thing that makes sense
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -catPath:Cat1/Sub Category/a, b & c");
		List<QueryStringTerm> result = analyze(underTest, "input");

		assertEquals(2, result.size(), () -> result.toString());
		WeightedWord term1 = assertAndCastInstanceOf(result.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("catPath", term2.getField());
		assertEquals("Cat1/Sub Category/a, b & c", term2.getWord());
		assertEquals(Occur.MUST_NOT, term2.getOccur());
	}

	@Test
	public void testExcludeMultipleCategoryPathFilters() {
		// actually this is invalid lucene syntax, but in this context it's the only thing that makes sense
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -catPath:Cat1/Sub Category/a, b & c -catPath:Cat2/Sub Cat/x, y & z");
		List<QueryStringTerm> result = analyze(underTest, "input");

		assertEquals(3, result.size(), () -> result.toString());
		WeightedWord term1 = assertAndCastInstanceOf(result.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("catPath", term2.getField());
		assertEquals("Cat1/Sub Category/a, b & c", term2.getWord());
		assertEquals(Occur.MUST_NOT, term2.getOccur());

		QueryFilterTerm term3 = assertAndCastInstanceOf(result.get(2), QueryFilterTerm.class);
		assertEquals("catPath", term3.getField());
		assertEquals("Cat2/Sub Cat/x, y & z", term3.getWord());
		assertEquals(Occur.MUST_NOT, term3.getOccur());
	}

	@Test
	public void testInsaneMultipleFilters() {
		// actually this is invalid lucene syntax, but in this context it's the only thing that makes sense
		QuerqyQueryExpander underTest = loadRule("input =>", "  FILTER: * -catPath:Cat1/Sub Category/a, b & c brand:\"Awesome Stuff\" price:12-100.23");
		List<QueryStringTerm> result = analyze(underTest, "input");

		assertEquals(4, result.size(), () -> result.toString());
		WeightedWord term1 = assertAndCastInstanceOf(result.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		QueryFilterTerm term2 = assertAndCastInstanceOf(result.get(1), QueryFilterTerm.class);
		assertEquals("catPath", term2.getField());
		assertEquals("Cat1/Sub Category/a, b & c", term2.getWord());
		assertEquals(Occur.MUST_NOT, term2.getOccur());

		QueryFilterTerm term3 = assertAndCastInstanceOf(result.get(2), QueryFilterTerm.class);
		assertEquals("brand", term3.getField());
		assertEquals("Awesome Stuff", term3.getWord());
		assertEquals(Occur.MUST, term3.getOccur());

		QueryFilterTerm term4 = assertAndCastInstanceOf(result.get(3), QueryFilterTerm.class);
		assertEquals("price", term4.getField());
		assertEquals("12-100.23", term4.getWord());
		assertEquals(Occur.MUST, term4.getOccur());
	}

	/**
	 * analyze and log results
	 * 
	 * @param query
	 * @return
	 */
	private List<QueryStringTerm> analyze(QuerqyQueryExpander underTest, String query) {
		List<QueryStringTerm> result = underTest.analyze(query);
		log.info("query '{}' returned result {}", query, result);
		return result;
	}
}
