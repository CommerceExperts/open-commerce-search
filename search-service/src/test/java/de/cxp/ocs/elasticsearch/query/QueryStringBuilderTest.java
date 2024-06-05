package de.cxp.ocs.elasticsearch.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.elasticsearch.core.List;
import org.junit.jupiter.api.Test;

import de.cxp.ocs.elasticsearch.model.query.*;
import de.cxp.ocs.elasticsearch.model.term.*;

public class QueryStringBuilderTest {

	@Test
	public void testSingleWord() {
		QueryStringBuilder underTest = new QueryStringBuilder();
		new ExtendedQuery(new SingleTermQuery(new WeightedTerm("banana", 1, Occur.MUST))).accept(underTest);
		assertEquals("+banana", underTest.getQueryString());
	}

	@Test
	public void testMatchAllQuery() {
		QueryStringBuilder underTest = new QueryStringBuilder();
		new ExtendedQuery(new MatchAllQuery()).accept(underTest);
		assertEquals("", underTest.getQueryString());
	}

	@Test
	public void testSingleWordWithTermFilters() {
		QueryStringBuilder underTest = new QueryStringBuilder();
		new ExtendedQuery(
				new SingleTermQuery(new WeightedTerm("banana", 1, Occur.SHOULD)),
				List.of(
						new WeightedTerm("peanutbutter", 1, Occur.MUST),
						new WeightedTerm("jelly", 1, Occur.MUST))
		//
		).accept(underTest);

		assertEquals("banana +peanutbutter +jelly", underTest.getQueryString());
	}

	@Test
	public void testSingleWordWithFieldFilter() {
		QueryStringBuilder underTest = new QueryStringBuilder();
		new ExtendedQuery(
				new SingleTermQuery(new WeightedTerm("banana", 1, Occur.SHOULD)),
				List.of(new QueryFilterTerm("brand", "bruno"))
		//
		).accept(underTest);

		assertEquals("banana +brand:bruno", underTest.getQueryString());
	}

	@Test
	public void testSimpleMultiTermQuery() {
		QueryStringBuilder underTest = new QueryStringBuilder();
		new ExtendedQuery(
				new MultiTermQuery(List.of(
						new WeightedTerm("banana", 1, Occur.SHOULD),
						new WeightedTerm("bread", 2, Occur.SHOULD))),
				List.of(new QueryFilterTerm("brand", "bruno", Occur.MUST_NOT))
		//
		).accept(underTest);

		assertEquals("banana bread^2.0 -brand:bruno", underTest.getQueryString());
	}

	@Test
	public void testMultiTermQueryWithSynonym() {
		QueryStringBuilder underTest = new QueryStringBuilder();
		new ExtendedQuery(
				new MultiTermQuery(
						List.of("banana bread"), // original terms
						List.of(
								new WeightedTerm("banana", 1, Occur.SHOULD),
								// bread => SYNONYM: toast
								new AssociatedTerm(new WeightedTerm("bread"), new WeightedTerm("toast", 0.8f)))),
				List.of(new QueryFilterTerm("brand", "bruno", Occur.MUST_NOT))
		//
		).accept(underTest);

		assertEquals("banana (bread OR toast^0.8) -brand:bruno", underTest.getQueryString());
	}

	@Test
	public void testMultiTermQueryWithSynonymAndConceptTerm() {
		QueryStringBuilder underTest = new QueryStringBuilder();

		new ExtendedQuery(
				new MultiTermQuery(
						List.of("peanut butter bread"), // original terms
						List.of(
								// concept: peanut butter
								new ConceptTerm(List.of(new WeightedTerm("peanut"), new WeightedTerm("butter"))).setQuoted(true),
								// bread => SYNONYM: toast
								new AssociatedTerm(new WeightedTerm("bread"), new WeightedTerm("toast", 0.8f)))),
				List.of(new QueryFilterTerm("brand", "bruno", Occur.MUST_NOT))
		//
		).accept(underTest);

		assertEquals("\"peanut butter\" (bread OR toast^0.8) -brand:bruno", underTest.getQueryString());
	}

	@Test
	public void testMultiTermQueryWithFuzzinessEnabled() {
		QueryStringBuilder underTest = new QueryStringBuilder().setAddFuzzyMarker(true);
		new ExtendedQuery(
				new MultiTermQuery(
						List.of("banana jelly bread"), // original terms
						List.of(
								new WeightedTerm("banana", 1, Occur.SHOULD),
								// quoted terms must not be fuzzy
								new WeightedTerm("jelly", 1, Occur.SHOULD).setQuoted(true),
								// bread => SYNONYM: toast. Since they are considered as known (injected trough rules),
								// those terms must not be fuzzy
								new AssociatedTerm(new WeightedTerm("bread"), new WeightedTerm("toast", 0.8f)))),
				// filters must not be fuzzy
				List.of(new QueryFilterTerm("brand", "bruno", Occur.MUST_NOT))
		//
		).accept(underTest);

		assertEquals("banana~ \"jelly\" (bread OR toast^0.8) -brand:bruno", underTest.getQueryString());
	}

	@Test
	public void testMultiVariantQuery() {
		QueryStringBuilder underTest = new QueryStringBuilder();
		new ExtendedQuery(
				new MultiVariantQuery(
						List.of("banana jelly bread"),
						List.of(
								new MultiTermQuery(
										List.of("banana jelly bread"),
										List.of(new WeightedTerm("banana"),
												new WeightedTerm("jelly"),
												new AssociatedTerm(new WeightedTerm("bread"), new WeightedTerm("toast", 0.8f)))),
								new MultiTermQuery(
										List.of("banana-jam bread"),
										List.of(new ConceptTerm(List.of(new WeightedTerm("banana"), new WeightedTerm("jam"))),
												new AssociatedTerm(new WeightedTerm("bread"), new WeightedTerm("toast", 0.8f))))
						// end of the two separate queries
						)
				// end of multi-variant-query
				),
				List.of(new QueryFilterTerm("brand", "bruno", Occur.MUST_NOT))
		//
		).accept(underTest);

		assertEquals("(banana jelly (bread OR toast^0.8)) OR ((banana jam) (bread OR toast^0.8)) -brand:bruno", underTest.getQueryString());
	}
}
