package de.cxp.ocs.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldType;
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
						.withFilter(new TermResultFilter(new Field("brand"), "bar")));
		String result = underTest.withoutFilterAsLink(new FacetConfig("Brand", "brand"), "bar");
		assertFalse(result.contains("brand=bar"), result);
	}

	@Test
	public void testAddMultiSelectFilter() {
		SearchQueryBuilder underTest = new SearchQueryBuilder(
				new InternalSearchParams()
						.setUserQuery("foo")
						.withFilter(new TermResultFilter(new Field("brand"), "apple")));
		String result = underTest.withFilterAsLink(
				new FacetConfig("Brand", "brand").setMultiSelect(true),
				"orange");
		assertTrue(result.contains("brand=apple%2Corange"), result);
	}

	@Test
	public void testSeveralFilters() {
		SearchQueryBuilder underTest = new SearchQueryBuilder(
				new InternalSearchParams()
						.setUserQuery("foo")
						.withFilter(new TermResultFilter(new Field("brand"), "apple", "orange")));
		String result = underTest.withFilterAsLink(
				new FacetConfig("Price", "price"), "0", "10");
		assertTrue(result.contains("brand=apple%2Corange"), result);
		assertTrue(result.contains("price=0%2C10"), result);
	}

	@Test
	public void testEncodedFilters() {
		SearchQueryBuilder underTest = new SearchQueryBuilder(
				new InternalSearchParams()
						.setUserQuery("foo")
						.withFilter(new TermResultFilter(new Field("brand"), "äpple")));
		String result = underTest.withFilterAsLink(
				new FacetConfig("Category", "cat"), "Männer", "Was für's Köpfchen, Mützen & Schals");
		assertTrue(result.contains("brand=%C3%A4pple"), result);
		assertTrue(result.contains("cat=M%C3%A4nner%2CWas+f%C3%BCr%27s+K%C3%B6pfchen%252C+M%C3%BCtzen+%26+Schals"), result);
	}

	@Test
	public void testRemoveMultiSelectFilter() {
		SearchQueryBuilder underTest = new SearchQueryBuilder(
				new InternalSearchParams()
						.setUserQuery("foo")
						.withFilter(new TermResultFilter(new Field("brand"), "apple", "orange")));
		String result = underTest.withoutFilterAsLink(
				new FacetConfig("Brand", "brand").setMultiSelect(true),
				"orange");
		assertTrue(result.contains("brand=apple"), result);
		assertFalse(result.matches("brand=.*orange"), result);
	}

	@Test
	public void testRemoveFilter() {
		SearchQueryBuilder underTest = new SearchQueryBuilder(
				new InternalSearchParams()
						.setUserQuery("foo")
						.withFilter(new TermResultFilter(new Field("brand"), "apple", "orange"))
						.withFilter(new NumberResultFilter(new Field("price").setType(FieldType.NUMBER), 1.23, 4.56)));
		String baseLink = underTest.toString();
		assertTrue(baseLink.contains("price=1.23%2C4.56"), baseLink);
		assertTrue(baseLink.contains("brand=apple%2Corange"), baseLink);

		String result = underTest.withoutFilterAsLink(new FacetConfig("Price", "price"), "1.23", "4.56");
		assertFalse(result.contains("price"), result);
		assertTrue(result.contains("brand=apple%2Corange"), result);
	}
}
