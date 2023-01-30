package de.cxp.ocs.elasticsearch.query.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.lucene.search.BooleanClause.Occur;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.cxp.ocs.elasticsearch.query.model.*;
import lombok.extern.slf4j.Slf4j;

/**
 * A test that indirectly also tests QuerqyQueryExpander.
 */
@Slf4j
public class AsciifyQuerqyQueryAnalyzerTest {

	static AsciifyQuerqyQueryAnalyzer underTest = new AsciifyQuerqyQueryAnalyzer();

	@BeforeAll
	public static void init() throws IOException {
		Path querqyRulesFile = Files.createTempFile("querqy_rules_test.", ".txt");
		Files.write(querqyRulesFile, Arrays.asList(
				// asciified rule
				"dzieci =>",
				"  SYNONYM: dzieciece",
				// rules for same term DiacriticChar and asciified
				"dzieciece =>",
				"  SYNONYM: dzieci",
				"  SYNONYM(0.5): chłopięce",
				"dziecięce =>",
				"  SYNONYM(0.5): dziewczęce",
				// non-ascii rule
				"zimowe =>",
				"  SYNONYM: śnieg",
				// rules for same term DiacriticChar and asciified including filter
				"buty =>",
				"  FILTER: -obuwie",
				"bùty =>",
				"  SYNONYM: booty",
				// rule with raw query filter
				"booty =>",
				"  FILTER: * category:footy"

		), StandardCharsets.UTF_8);

		underTest.initialize(Collections.singletonMap("common_rules_url", querqyRulesFile.toAbsolutePath().toString()));

	}

	@Test
	public void testIfQuerqyIsUsed() {
		List<QueryStringTerm> result = analyze("dzieci");
		assertEquals(1, result.size());
		WordAssociation term = assertAndCastInstanceOf(result.get(0), WordAssociation.class);
		assertEquals("dzieci", term.getOriginalWord());

		assertTrue(term.getRelatedWords().keySet().contains("dzieciece"));
		assertEquals(1, term.getRelatedWords().size(), term.getRelatedWords()::toString);
	}

	@Test
	public void testIfAsciifiedRuleIsUsedForNonAsciiTerm() {
		List<QueryStringTerm> result = analyze("dzięci");
		assertEquals(1, result.size());

		WordAssociation term = assertAndCastInstanceOf(result.get(0), WordAssociation.class);
		assertEquals("dzieci", term.getOriginalWord());

		assertTrue(term.getRelatedWords().keySet().contains("dzięci"));
		assertTrue(term.getRelatedWords().keySet().contains("dzieciece"));
		assertEquals(2, term.getRelatedWords().size(), term.getRelatedWords()::toString);
	}

	@Test
	public void testAsciifiedAndDiacriticTermsWithoutRulesAreCombined() {
		List<QueryStringTerm> result = analyze("chłopięce");
		assertEquals(1, result.size());

		AlternativeTerm term = assertAndCastInstanceOf(result.get(0), AlternativeTerm.class);
		assertEquals("chłopięce", term.getAlternatives().get(0).getWord());
		assertEquals("chlopiece", term.getAlternatives().get(1).getWord());
	}

	@Test
	public void testDiacriticCharQueryMatchesBothRule() {
		List<QueryStringTerm> result = analyze("dziecięce");
		assertEquals(1, result.size());
		assertWordAssociation_dziecięce(result.get(0), "dziecięce");
	}

	@Test
	public void testAsciifiedQueryMatchesOnlyAsciifiedRule() {
		List<QueryStringTerm> result = analyze("dzieciece");
		assertEquals(1, result.size());
		assertWordAssociation_dziecięce(result.get(0), "dzieciece");
	}

	private void assertWordAssociation_dziecięce(QueryStringTerm term, String userSpelling) {
		WordAssociation wordAssoc = assertAndCastInstanceOf(term, WordAssociation.class);
		assertEquals(userSpelling, wordAssoc.getOriginalWord());

		// ASCIIfying just works one-directional. If the ASCIIfyied term was searched by the user, the rule for the
		// DiacriticChar term is not expected to match. That works only if the DiacriticChar term was used by the user
		int expectedRelatedWordCount = 2;
		if ("dziecięce".equals(userSpelling)) {
			assertTrue(wordAssoc.getRelatedWords().keySet().contains("dzieciece"));
			assertTrue(wordAssoc.getRelatedWords().keySet().contains("dziewczęce"));
			expectedRelatedWordCount += 2;
		}

		assertTrue(wordAssoc.getRelatedWords().keySet().contains("dzieci"));
		assertTrue(wordAssoc.getRelatedWords().keySet().contains("chłopięce"));
		assertEquals(expectedRelatedWordCount, wordAssoc.getRelatedWords().size()); // expect no more
	}

	@Test
	public void testMultiTermQueryMatchesDifferentRule() {
		List<QueryStringTerm> result = analyze("dziecięce zimowe");
		assertEquals(2, result.size());
		assertWordAssociation_dziecięce(result.get(0), "dziecięce");
		assertWordAssociation_zimowe(result.get(1));
	}

	private void assertWordAssociation_zimowe(QueryStringTerm term) {
		WordAssociation wordAssoc = assertAndCastInstanceOf(term, WordAssociation.class);
		assertEquals("zimowe", wordAssoc.getOriginalWord());
		assertTrue(wordAssoc.getRelatedWords().keySet().contains("śnieg"));
		assertEquals(1, wordAssoc.getRelatedWords().size()); // expect no more
	}

	@Test
	public void testMultiTermMatchesButOneWithoutRule() {
		List<QueryStringTerm> result = analyze("dzieciece zimowe obuwie");
		assertEquals(3, result.size());
		assertWordAssociation_dziecięce(result.get(0), "dzieciece");
		assertWordAssociation_zimowe(result.get(1));
		assertEquals("obuwie", result.get(2).getWord());
	}

	@Test
	public void testDiacriticCharTermMatchesAsciifiedFilterRule() throws InterruptedException, ExecutionException {
		List<QueryStringTerm> result = analyze("bùty");
		assertEquals(2, result.size());
		assertWordAssociation_bùty(result.get(0));
		testFilter_obuwie(result.get(1));
	}

	private void testFilter_obuwie(QueryStringTerm term) {
		WeightedWord filterTerm = assertAndCastInstanceOf(term, WeightedWord.class);
		assertEquals("obuwie", filterTerm.getWord());
		assertEquals(Occur.MUST_NOT, filterTerm.getOccur());
	}

	private void assertWordAssociation_bùty(QueryStringTerm term) {
		WordAssociation wordAssoc = assertAndCastInstanceOf(term, WordAssociation.class);
		assertEquals("bùty", wordAssoc.getOriginalWord());
		assertTrue(wordAssoc.getRelatedWords().keySet().contains("buty"));
		assertTrue(wordAssoc.getRelatedWords().keySet().contains("booty"));
		assertEquals(2, wordAssoc.getRelatedWords().size()); // expect no more
	}

	@Test
	public void testMultipleTermsMatchesAllKindOfRule() throws InterruptedException, ExecutionException {
		List<QueryStringTerm> result = analyze("dziecięce zimowe bùty");
		assertEquals(3 + 1, result.size());

		assertWordAssociation_dziecięce(result.get(0), "dziecięce");
		assertWordAssociation_zimowe(result.get(1));
		assertWordAssociation_bùty(result.get(2));
		testFilter_obuwie(result.get(3));
	}

	@Test
	public void testRawFilterRule() throws InterruptedException, ExecutionException {
		List<QueryStringTerm> result = analyze("any booty");
		assertEquals(3, result.size());

		WeightedWord anyTerm = assertAndCastInstanceOf(result.get(0), WeightedWord.class);
		assertEquals("any", anyTerm.getWord());
		assertEquals(1f, anyTerm.getWeight());

		WeightedWord bootyTerm = assertAndCastInstanceOf(result.get(1), WeightedWord.class);
		assertEquals("booty", bootyTerm.getWord());
		assertEquals(1f, bootyTerm.getWeight());

		QueryFilterTerm filterTerm = assertAndCastInstanceOf(result.get(2), QueryFilterTerm.class);
		assertEquals("category", filterTerm.getField());
		assertEquals("footy", filterTerm.getWord());
		assertEquals(Occur.MUST, filterTerm.getOccur());
	}

	/**
	 * analyze and log results
	 * 
	 * @param query
	 * @return
	 */
	private List<QueryStringTerm> analyze(String query) {
		List<QueryStringTerm> result = underTest.analyze(query);
		log.info("query '{}' returned result {}", query, result);
		return result;
	}

	private <T> T assertAndCastInstanceOf(QueryStringTerm term, Class<T> clazz) {
		assertTrue(clazz.isAssignableFrom(term.getClass()), "expected object of type " + clazz + " but was " + term.getClass());
		return clazz.cast(term);
	}
}
