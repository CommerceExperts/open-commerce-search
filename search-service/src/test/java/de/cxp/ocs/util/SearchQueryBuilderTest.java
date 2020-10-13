package de.cxp.ocs.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilter;

public class SearchQueryBuilderTest {

	@Test
	public void testAddingFilter() {
		SearchQueryBuilder underTest = new SearchQueryBuilder(new InternalSearchParams().setUserQuery("foo"));
		String result = underTest.withFilterAsLink(new FacetConfig("Brand", "brand"), "bar");
		assertTrue(result.contains("brand=bar"), result);
	}

	@Test
	public void testRemovingFilter() {
		SearchQueryBuilder underTest = new SearchQueryBuilder(
				new InternalSearchParams()
						.setUserQuery("foo")
						.withFilter(new TermResultFilter("brand", "bar")));
		String result = underTest.withoutFilterAsLink(new FacetConfig("Brand", "brand"), "bar");
		assertFalse(result.contains("brand=bar"), result);
	}

	@Test
	public void testAddMultiSelectFilter() {
		SearchQueryBuilder underTest = new SearchQueryBuilder(
				new InternalSearchParams()
						.setUserQuery("foo")
						.withFilter(new TermResultFilter("brand", "apple")));
		String result = underTest.withFilterAsLink(
				new FacetConfig("Brand", "brand").setMultiSelect(true),
				"orange");
		assertTrue(result.contains("brand=apple,orange"), result);
	}

	@Test
	public void testRemoveMultiSelectFilter() {
		SearchQueryBuilder underTest = new SearchQueryBuilder(
				new InternalSearchParams()
						.setUserQuery("foo")
						.withFilter(new TermResultFilter("brand", "apple", "orange")));
		String result = underTest.withoutFilterAsLink(
				new FacetConfig("Brand", "brand").setMultiSelect(true),
				"orange");
		assertTrue(result.contains("brand=apple"), result);
		assertFalse(result.matches("brand=.*orange"), result);
	}

	@Test
	public void testSomethingElse() {
		SearchQueryBuilder underTest = new SearchQueryBuilder(
				new InternalSearchParams()
						.setUserQuery("foo")
						.withFilter(new TermResultFilter("brand", "apple", "orange"))
						.withFilter(new NumberResultFilter("price", 1.23, 4.56)));
		String baseLink = underTest.toString();
		assertTrue(baseLink.contains("price=1.23,4.56"), baseLink);
		assertTrue(baseLink.contains("brand=apple,orange"), baseLink);

		String result = underTest.withoutFilterAsLink(new FacetConfig("Price", "price"), "1.23,4.56");
		assertFalse(result.contains("price"), result);
		assertTrue(result.contains("brand=apple,orange"), result);
	}
}
