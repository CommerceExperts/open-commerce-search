package de.cxp.ocs.util;

import static de.cxp.ocs.util.SearchParamsParser.parseFilters;
import static de.cxp.ocs.util.SearchParamsParser.parseSortings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.config.*;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilter;
import de.cxp.ocs.model.result.SortOrder;
import de.cxp.ocs.model.result.Sorting;

public class SearchParamsParserTest {

	private FieldConfiguration	fields		= new FieldConfiguration()
			.addField(new Field().setName("price").setType(FieldType.NUMBER).setUsage(FieldUsage.FACET, FieldUsage.SORT))
			.addField(new Field().setName("brand").setUsage(FieldUsage.FACET))
			.addField(new Field().setName("image").setUsage(FieldUsage.RESULT));
	private FieldConfigIndex	fieldConfIndex	= new FieldConfigIndex(fields);

	@Test
	public void parseNormalNumericRangeFilter() {
		List<InternalResultFilter> parsedFilters = parseFilters(Collections.singletonMap("price", "12,34"), fieldConfIndex, Locale.ROOT);
		assertEquals(1, parsedFilters.size());
		assertTrue(parsedFilters.get(0) instanceof NumberResultFilter);
		NumberResultFilter priceFilter = (NumberResultFilter) parsedFilters.get(0);
		assertEquals(2, priceFilter.getValues().length);
		assertEquals(12, priceFilter.getLowerBound());
		assertEquals(34, priceFilter.getUpperBound());
	}

	@Test
	public void parseFallbackNumericRangeFilter() {
		List<InternalResultFilter> parsedFilters = parseFilters(Collections.singletonMap("price", "12 - 34"), fieldConfIndex, Locale.ROOT);
		assertEquals(1, parsedFilters.size());
		assertTrue(parsedFilters.get(0) instanceof NumberResultFilter);
		NumberResultFilter priceFilter = (NumberResultFilter) parsedFilters.get(0);
		assertEquals(2, priceFilter.getValues().length);
		assertEquals(12, priceFilter.getLowerBound());
		assertEquals(34, priceFilter.getUpperBound());
	}

	@Test
	public void parseTextFilter() {
		List<InternalResultFilter> parsedFilters = parseFilters(Collections.singletonMap("brand", "foo,bar,pum"), fieldConfIndex, Locale.ROOT);
		assertEquals(1, parsedFilters.size());

		assertTrue(parsedFilters.get(0) instanceof TermResultFilter);
		TermResultFilter brand = (TermResultFilter) parsedFilters.get(0);
		assertEquals(3, brand.getValues().length);
	}

	@Test
	public void ignoreNonFacetParameter() {
		List<InternalResultFilter> parsedFilters = parseFilters(Collections.singletonMap("image", "awesome.jpg"), fieldConfIndex, Locale.ROOT);
		assertEquals(0, parsedFilters.size());
	}

	@Test
	public void testSortParsing() {
		// "image" should be ignored, because its not sortable
		List<Sorting> sortings = parseSortings("image,-price", fieldConfIndex);
		assertEquals(1, sortings.size());
		assertEquals("price", sortings.get(0).field);
		assertEquals(SortOrder.DESC, sortings.get(0).sortOrder);
	}
}
