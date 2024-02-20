package de.cxp.ocs.elasticsearch;

import static de.cxp.ocs.util.TestUtils.assertAndCastInstanceOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldConfiguration;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.query.SingleTermQuery;
import de.cxp.ocs.elasticsearch.model.term.Occur;
import de.cxp.ocs.elasticsearch.model.term.QueryFilterTerm;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.model.term.WeightedTerm;
import de.cxp.ocs.elasticsearch.query.analyzer.AnalyzerUtil;
import de.cxp.ocs.elasticsearch.query.analyzer.WhitespaceAnalyzer;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import de.cxp.ocs.util.InternalSearchParams;

public class QueryStringParserTest {

	@Test
	public void simpleWhitespaceParsing() {
		QueryStringParser underTest = new QueryStringParser(new WhitespaceAnalyzer(), new FieldConfigIndex(new FieldConfiguration()), Locale.ROOT);
		ExtendedQuery parsedQuery = underTest.preprocessQuery(paramsWithQuery("foo bar"), new HashMap<>());
		List<QueryStringTerm> queryTerms = AnalyzerUtil.extractTerms(parsedQuery);

		assertEquals(2, queryTerms.size());
		WeightedTerm term1 = assertAndCastInstanceOf(queryTerms.get(0), WeightedTerm.class);
		assertEquals("foo", term1.getRawTerm());

		WeightedTerm term2 = assertAndCastInstanceOf(queryTerms.get(1), WeightedTerm.class);
		assertEquals("bar", term2.getRawTerm());
	}

	@Test
	public void inducedNegatedFiltersOnSameField() {
		UserQueryAnalyzer analyzerMock = new UserQueryAnalyzer() {

			@Override
			public ExtendedQuery analyze(String userQuery) {
				return new ExtendedQuery(
						new SingleTermQuery(new WeightedTerm("input")),
						// expect to be ignored:
						Arrays.asList(new QueryFilterTerm("brand", "b1", Occur.MUST_NOT), new QueryFilterTerm("brand", "b2", Occur.MUST_NOT)));
			}
		};

		QueryStringParser underTest = new QueryStringParser(analyzerMock, new FieldConfigIndex(new FieldConfiguration().addField(new Field("brand").setUsage(FieldUsage.FACET))), Locale.ROOT);
		InternalSearchParams internalParams = paramsWithQuery("input");
		ExtendedQuery parsedQuery = underTest.preprocessQuery(internalParams, new HashMap<>());
		List<QueryStringTerm> queryTerms = AnalyzerUtil.extractTerms(parsedQuery);

		assertEquals(1, queryTerms.size());
		WeightedTerm term1 = assertAndCastInstanceOf(queryTerms.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		assertEquals(1, internalParams.inducedFilters.size());
		InternalResultFilter brandFilter = assertAndCastInstanceOf(internalParams.inducedFilters.get(0), InternalResultFilter.class);
		assertArrayEquals(new String[] { "b2", "b1" }, brandFilter.getValues());
		assertTrue(brandFilter.isNegated());
	}

	@Test
	public void combinedIncludeAndExcludeFiltersOnSameField() {
		UserQueryAnalyzer analyzerMock = new UserQueryAnalyzer() {

			@Override
			public ExtendedQuery analyze(String userQuery) {
				return new ExtendedQuery(
						new SingleTermQuery(new WeightedTerm("input")),
						Arrays.asList(
								new QueryFilterTerm("brand", "b1", Occur.MUST),
								// expect to be ignored:
								new QueryFilterTerm("brand", "b2", Occur.MUST_NOT)));
			}
		};

		QueryStringParser underTest = new QueryStringParser(analyzerMock, new FieldConfigIndex(new FieldConfiguration().addField(new Field("brand").setUsage(FieldUsage.FACET))), Locale.ROOT);
		InternalSearchParams internalParams = paramsWithQuery("input");
		ExtendedQuery parsedQuery = underTest.preprocessQuery(internalParams, new HashMap<>());
		List<QueryStringTerm> queryTerms = AnalyzerUtil.extractTerms(parsedQuery);

		assertEquals(1, queryTerms.size());
		WeightedTerm term1 = assertAndCastInstanceOf(queryTerms.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		assertEquals(1, internalParams.inducedFilters.size());
		InternalResultFilter brandFilter = assertAndCastInstanceOf(internalParams.inducedFilters.get(0), InternalResultFilter.class);
		assertArrayEquals(new String[] { "b1" }, brandFilter.getValues());
		assertFalse(brandFilter.isNegated());
	}

	// inverted version to combinedIncludeAndExcludeFiltersOnSameField
	@Test
	public void combinedExcludeAndIncludeFiltersOnSameField() {
		UserQueryAnalyzer analyzerMock = new UserQueryAnalyzer() {

			@Override
			public ExtendedQuery analyze(String userQuery) {
				return new ExtendedQuery(
						new SingleTermQuery(new WeightedTerm("input")),
						Arrays.asList(
								// expect to be ignored:
								new QueryFilterTerm("brand", "b1", Occur.MUST_NOT),
								new QueryFilterTerm("brand", "b2", Occur.MUST)));
			}
		};

		QueryStringParser underTest = new QueryStringParser(analyzerMock, new FieldConfigIndex(new FieldConfiguration().addField(new Field("brand").setUsage(FieldUsage.FACET))), Locale.ROOT);
		InternalSearchParams internalParams = paramsWithQuery("input");
		ExtendedQuery parsedQuery = underTest.preprocessQuery(internalParams, new HashMap<>());
		List<QueryStringTerm> queryTerms = AnalyzerUtil.extractTerms(parsedQuery);

		assertEquals(1, queryTerms.size());
		WeightedTerm term1 = assertAndCastInstanceOf(queryTerms.get(0), WeightedTerm.class);
		assertEquals("input", term1.getRawTerm());

		assertEquals(1, internalParams.inducedFilters.size());
		InternalResultFilter brandFilter = assertAndCastInstanceOf(internalParams.inducedFilters.get(0), InternalResultFilter.class);
		assertArrayEquals(new String[] { "b2" }, brandFilter.getValues());
		assertFalse(brandFilter.isNegated());
	}

	private InternalSearchParams paramsWithQuery(String query) {
		return new InternalSearchParams().setUserQuery(query);
	}
}
