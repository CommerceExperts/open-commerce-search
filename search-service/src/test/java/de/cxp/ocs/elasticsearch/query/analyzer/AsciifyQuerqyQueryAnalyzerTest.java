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

import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.term.AssociatedTerm;
import de.cxp.ocs.elasticsearch.model.term.QueryFilterTerm;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.model.term.WeightedTerm;
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
		ExtendedQuery analyzedQuery = analyze("dzieci");
		List<QueryStringTerm> result = AnalyzerUtil.extractTerms(analyzedQuery);

		assertEquals(1, result.size());
		AssociatedTerm term = assertAndCastInstanceOf(result.get(0), AssociatedTerm.class);
		assertEquals("dzieci", term.getRawTerm());

		assertTrue(term.getRelatedTerms().keySet().contains("dzieciece"));
		assertEquals(1, term.getRelatedTerms().size(), term.getRelatedTerms()::toString);
	}

	@Test
	public void testUppercaseConversionInRules() {
		ExtendedQuery analyzedQuery = analyze("dzięci");
		assertEquals("(dzięci) OR (dzieci OR dzieciece)", analyzedQuery.toQueryString());
	}

	@Test
	public void testAsciifiedAndDiacriticTermsWithoutRulesAreCombined() {
		ExtendedQuery analyzedQuery = analyze("chłopięce");
		List<QueryStringTerm> result = AnalyzerUtil.extractTerms(analyzedQuery);

		assertEquals(2, result.size(), result::toString);

		WeightedTerm term1 = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("chłopięce", term1.getRawTerm());

		WeightedTerm term2 = assertAndCastInstanceOf(result.get(1), WeightedTerm.class);
		assertEquals("chlopiece", term2.getRawTerm());
	}

	@Test
	public void testDiacriticCharQueryMatchesBothRule() {
		var analyzedQuery = analyze("dziecięce");
		assertEquals("(dziecięce OR dziewczęce^0.5) OR (dzieciece OR chłopięce^0.5 OR dzieci)", analyzedQuery.toQueryString());
	}

	@Test
	public void testAsciifiedQueryMatchesOnlyAsciifiedRule() {
		ExtendedQuery analyzedQuery = analyze("dzieciece");
		List<QueryStringTerm> result = AnalyzerUtil.extractTerms(analyzedQuery);

		assertEquals(1, result.size());
		assertWordAssociation_dziecięce(result.get(0), "dzieciece");
	}

	private void assertWordAssociation_dziecięce(QueryStringTerm term, String userSpelling) {
		AssociatedTerm wordAssoc = assertAndCastInstanceOf(term, AssociatedTerm.class);
		assertEquals(userSpelling, wordAssoc.getRawTerm());

		// ASCIIfying just works one-directional. If the ASCIIfyied term was searched by the user, the rule for the
		// DiacriticChar term is not expected to match. That works only if the DiacriticChar term was used by the user
		int expectedRelatedWordCount = 2;
		if ("dziecięce".equals(userSpelling)) {
			assertTrue(wordAssoc.getRelatedTerms().keySet().contains("dzieciece"));
			assertTrue(wordAssoc.getRelatedTerms().keySet().contains("dziewczęce"));
			expectedRelatedWordCount += 2;
		}

		assertTrue(wordAssoc.getRelatedTerms().keySet().contains("dzieci"));
		assertTrue(wordAssoc.getRelatedTerms().keySet().contains("chłopięce"));
		assertEquals(expectedRelatedWordCount, wordAssoc.getRelatedTerms().size()); // expect no more
	}

	@Test
	public void testMultiTermQueryMatchesDifferentRule() {
		ExtendedQuery analyzedQuery = analyze("dziecięce zimowe");
		assertEquals("((dziecięce OR dziewczęce^0.5) (zimowe OR śnieg)) OR ((dzieciece OR chłopięce^0.5 OR dzieci) (zimowe OR śnieg))", analyzedQuery.toQueryString());
	}

	private void assertWordAssociation_zimowe(QueryStringTerm term) {
		AssociatedTerm wordAssoc = assertAndCastInstanceOf(term, AssociatedTerm.class);
		assertEquals("zimowe", wordAssoc.getRawTerm());
		assertTrue(wordAssoc.getRelatedTerms().keySet().contains("śnieg"));
		assertEquals(1, wordAssoc.getRelatedTerms().size()); // expect no more
	}

	@Test
	public void testMultiTermMatchesButOneWithoutRule() {
		ExtendedQuery analyzedQuery = analyze("dzieciece zimowe obuwie");
		List<QueryStringTerm> result = AnalyzerUtil.extractTerms(analyzedQuery);

		assertEquals(3, result.size());
		assertWordAssociation_dziecięce(result.get(0), "dzieciece");
		assertWordAssociation_zimowe(result.get(1));
		assertEquals("obuwie", result.get(2).getRawTerm());
	}

	@Test
	public void testDiacriticCharTermMatchesAsciifiedFilterRule() throws InterruptedException, ExecutionException {
		var analyzedQuery = analyze("bùty");
		assertEquals("(bùty OR booty) OR (buty) -\"obuwie\"", analyzedQuery.toQueryString());
	}

	@Test
	public void testMultipleTermsMatchesAllKindOfRule() throws InterruptedException, ExecutionException {
		var analyzedQuery = analyze("dziecięce zimowe bùty");
		assertEquals("((dziecięce OR dziewczęce^0.5) (zimowe OR śnieg) (bùty OR booty)) OR ((dzieciece OR chłopięce^0.5 OR dzieci) (zimowe OR śnieg) buty) -\"obuwie\"", analyzedQuery.toQueryString());
	}

	@Test
	public void testRawFilterRule() throws InterruptedException, ExecutionException {
		ExtendedQuery analyzedQuery = analyze("any booty");
		List<QueryStringTerm> result = AnalyzerUtil.extractTerms(analyzedQuery);

		assertEquals(3, result.size());

		WeightedTerm anyTerm = assertAndCastInstanceOf(result.get(0), WeightedTerm.class);
		assertEquals("any", anyTerm.getRawTerm());
		assertEquals(1f, anyTerm.getWeight());

		WeightedTerm bootyTerm = assertAndCastInstanceOf(result.get(1), WeightedTerm.class);
		assertEquals("booty", bootyTerm.getRawTerm());
		assertEquals(1f, bootyTerm.getWeight());

		QueryFilterTerm filterTerm = assertAndCastInstanceOf(result.get(2), QueryFilterTerm.class);
		assertEquals("category", filterTerm.getField());
		assertEquals("footy", filterTerm.getRawTerm());
		assertEquals(Occur.MUST, filterTerm.getOccur());
	}

	/**
	 * analyze and log results
	 * 
	 * @param query
	 * @return
	 */
	private ExtendedQuery analyze(String query) {
		ExtendedQuery result = underTest.analyze(query);
		log.info("query '{}' returned result {}", query, result.toQueryString());
		return result;
	}

	private <T> T assertAndCastInstanceOf(QueryStringTerm term, Class<T> clazz) {
		assertTrue(clazz.isAssignableFrom(term.getClass()), "expected object of type " + clazz + " but was " + term.getClass());
		return clazz.cast(term);
	}
}
