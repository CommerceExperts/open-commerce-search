package de.cxp.ocs.util;

import static de.cxp.ocs.util.SearchParamsParser.parseFilters;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldConfiguration;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;

public class SearchParamsParserTest {

	private FieldConfigIndex fieldConfig = new FieldConfigIndex(
			new FieldConfiguration()
					.addField(new Field().setName("price").setType(FieldType.number).setUsage(FieldUsage.Facet)));

	@Test
	public void parseNormalNumericRangeFilter() {
		List<InternalResultFilter> parsedFilters = parseFilters(Collections.singletonMap("price", "12,34"), fieldConfig);
		assertEquals(1, parsedFilters.size());
		assertTrue(parsedFilters.get(0) instanceof NumberResultFilter);
		NumberResultFilter priceFilter = (NumberResultFilter) parsedFilters.get(0);
		assertEquals(2, priceFilter.getValues().length);
		assertEquals(12, priceFilter.getLowerBound());
		assertEquals(34, priceFilter.getUpperBound());
	}

	@Test
	public void parseFallbackNumericRangeFilter() {
		List<InternalResultFilter> parsedFilters = parseFilters(Collections.singletonMap("price", "12 - 34"), fieldConfig);
		assertEquals(1, parsedFilters.size());
		assertTrue(parsedFilters.get(0) instanceof NumberResultFilter);
		NumberResultFilter priceFilter = (NumberResultFilter) parsedFilters.get(0);
		assertEquals(2, priceFilter.getValues().length);
		assertEquals(12, priceFilter.getLowerBound());
		assertEquals(34, priceFilter.getUpperBound());
	}
}
