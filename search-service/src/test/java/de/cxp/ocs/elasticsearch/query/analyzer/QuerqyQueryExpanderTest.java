package de.cxp.ocs.elasticsearch.query.analyzer;

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
	public void testTwoWordSynonym() {
		QuerqyQueryExpander underTest = loadRule("input =>", "  SYNONYM(0.2): two words");
		List<QueryStringTerm> result = analyze(underTest, "input");
		assertEquals(1, result.size());
		WordAssociation term = assertAndCastInstanceOf(result.get(0), WordAssociation.class);
		assertEquals("input", term.getOriginalWord());

		assertEquals(1, term.getRelatedWords().size(), term.getRelatedWords()::toString);
		assertTrue(term.getRelatedWords().keySet().contains("two words"));

		WeightedWord relatedWords = assertAndCastInstanceOf(term.getRelatedWords().get("two words"), WeightedWord.class);
		assertEquals(0.2f, relatedWords.getWeight());
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

	private <T> T assertAndCastInstanceOf(QueryStringTerm term, Class<T> clazz) {
		assertTrue(clazz.isAssignableFrom(term.getClass()), "expected object of type " + clazz + " but was " + term.getClass());
		return clazz.cast(term);
	}
}
