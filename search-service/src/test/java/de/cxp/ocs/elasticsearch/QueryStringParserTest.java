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

import org.apache.lucene.search.BooleanClause.Occur;
import org.junit.jupiter.api.Test;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldConfiguration;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.elasticsearch.query.analyzer.WhitespaceAnalyzer;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.model.QueryFilterTerm;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WeightedWord;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import de.cxp.ocs.util.InternalSearchParams;

public class QueryStringParserTest {

	@Test
	public void simpleWhitespaceParsing() {
		QueryStringParser underTest = new QueryStringParser(new WhitespaceAnalyzer(), new FieldConfigIndex(new FieldConfiguration()), Locale.ROOT);
		List<QueryStringTerm> queryTerms = underTest.preprocessQuery(paramsWithQuery("foo bar"), new HashMap<>());

		assertEquals(2, queryTerms.size());
		WeightedWord term1 = assertAndCastInstanceOf(queryTerms.get(0), WeightedWord.class);
		assertEquals("foo", term1.getWord());

		WeightedWord term2 = assertAndCastInstanceOf(queryTerms.get(1), WeightedWord.class);
		assertEquals("bar", term2.getWord());
	}

	@Test
	public void inducedNegatedFiltersOnSameField() {
		UserQueryAnalyzer analyzerMock = new UserQueryAnalyzer() {

			@Override
			public List<QueryStringTerm> analyze(String userQuery) {
				return Arrays.asList(new WeightedWord("input"),
						new QueryFilterTerm("brand", "b1", Occur.MUST_NOT),
						new QueryFilterTerm("brand", "b2", Occur.MUST_NOT));
			}
		};

		QueryStringParser underTest = new QueryStringParser(analyzerMock, new FieldConfigIndex(new FieldConfiguration().addField(new Field("brand").setUsage(FieldUsage.FACET))), Locale.ROOT);
		InternalSearchParams internalParams = paramsWithQuery("input");
		List<QueryStringTerm> queryTerms = underTest.preprocessQuery(internalParams, new HashMap<>());

		assertEquals(1, queryTerms.size());
		WeightedWord term1 = assertAndCastInstanceOf(queryTerms.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		assertEquals(1, internalParams.querqyFilters.size());
		InternalResultFilter brandFilter = assertAndCastInstanceOf(internalParams.querqyFilters.get(0), InternalResultFilter.class);
		assertArrayEquals(new String[] { "b1", "b2" }, brandFilter.getValues());
		assertTrue(brandFilter.isNegated());
	}

	@Test
	public void combinedIncludeAndExcludeFiltersOnSameField() {
		UserQueryAnalyzer analyzerMock = new UserQueryAnalyzer() {

			@Override
			public List<QueryStringTerm> analyze(String userQuery) {
				return Arrays.asList(new WeightedWord("input"),
						new QueryFilterTerm("brand", "b1", Occur.MUST),
						// expect to be ignored:
						new QueryFilterTerm("brand", "b2", Occur.MUST_NOT));
			}
		};

		QueryStringParser underTest = new QueryStringParser(analyzerMock, new FieldConfigIndex(new FieldConfiguration().addField(new Field("brand").setUsage(FieldUsage.FACET))), Locale.ROOT);
		InternalSearchParams internalParams = paramsWithQuery("input");
		List<QueryStringTerm> queryTerms = underTest.preprocessQuery(internalParams, new HashMap<>());

		assertEquals(1, queryTerms.size());
		WeightedWord term1 = assertAndCastInstanceOf(queryTerms.get(0), WeightedWord.class);
		assertEquals("input", term1.getWord());

		assertEquals(1, internalParams.querqyFilters.size());
		InternalResultFilter brandFilter = assertAndCastInstanceOf(internalParams.querqyFilters.get(0), InternalResultFilter.class);
		assertArrayEquals(new String[] { "b1" }, brandFilter.getValues());
		assertFalse(brandFilter.isNegated());
	}

	private InternalSearchParams paramsWithQuery(String query) {
		return new InternalSearchParams().setUserQuery(query);
	}
}
