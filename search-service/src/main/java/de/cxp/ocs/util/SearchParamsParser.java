package de.cxp.ocs.util;

import static de.cxp.ocs.util.SearchQueryBuilder.SORT_DESC_PREFIX;
import static de.cxp.ocs.util.SearchQueryBuilder.VALUE_DELIMITER;
import static org.apache.commons.lang3.StringUtils.split;
import static org.apache.commons.lang3.StringUtils.splitPreserveAllTokens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilter;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.SortOrder;
import de.cxp.ocs.model.result.Sorting;

/**
 * Utility class to parse search parameters
 */
public class SearchParamsParser {

	public final static String ID_FILTER_SUFFIX = ".id";

	public static InternalSearchParams extractInternalParams(SearchQuery searchQuery, Map<String, String> filters, SearchContext searchContext) {
		final InternalSearchParams parameters = new InternalSearchParams();
		parameters.limit = searchQuery.limit;
		parameters.offset = searchQuery.offset;
		parameters.withFacets = searchQuery.withFacets;
		parameters.userQuery = searchQuery.q;

		if (searchQuery.sort != null) {
			parameters.sortings = parseSortings(searchQuery.sort, searchContext.getFieldConfigIndex());
		}
		parameters.filters = parseFilters(filters, searchContext.getFieldConfigIndex());

		Map<String, String> customParams = new HashMap<>(filters);
		parameters.filters.forEach(f -> {
			customParams.remove(f.getField().getName());
			customParams.remove(f.getField().getName() + SearchParamsParser.ID_FILTER_SUFFIX);
		});
		parameters.customParams = customParams;

		return parameters;
	}

	/**
	 * Checks the parameter map for valid filters and extracts them into
	 * InternalResultFilter objects.
	 * 
	 * @param filterValues
	 *        parameters as sent in the request
	 * @param fieldConfig
	 *        the field configuration
	 * @return validated and enriched filter values for internal usage
	 */
	public static List<InternalResultFilter> parseFilters(Map<String, String> filterValues, FieldConfigIndex fieldConfig) {
		List<InternalResultFilter> filters = new ArrayList<>();

		for (Entry<String, String> p : filterValues.entrySet()) {
			// special handling for spring: filters maps contains all parameters
			// and the mapping result objects
			if (!(p.getValue() instanceof String)) continue;

			String paramName = p.getKey();
			String paramValue = p.getValue();

			boolean isIdFilter = false;
			if (paramName.endsWith(ID_FILTER_SUFFIX)) {
				isIdFilter = true;
				paramName = paramName.substring(0, paramName.length() - 3);
			}

			Optional<Field> matchingField = fieldConfig.getMatchingField(paramName, paramValue, FieldUsage.FACET);

			if (matchingField.isPresent()) {
				Field field = matchingField.get();
				switch (field.getType()) {
					case CATEGORY:
						filters.add(new TermResultFilter(field, split(paramValue, VALUE_DELIMITER))
										.setFieldPrefix(FieldConstants.PATH_FACET_DATA)
										.setFilterOnId(isIdFilter));
						break;
					case NUMBER:
						String[] paramValues = splitPreserveAllTokens(paramValue, VALUE_DELIMITER);
						if (paramValues.length != 2) {
							// Fallback logic to allow numeric filter values
							// separated by dash, e.g. "50 - 100"
							// however this is error prone, because dash is
							// also used as minus for negative values. In such
							// case this will simply fail
							paramValues = splitPreserveAllTokens(paramValue, '-');
							if (paramValues.length != 2) {
								throw new IllegalArgumentException("unexpected numeric filter value: " + paramValue);
							}
						}
						filters.add(new NumberResultFilter(
								field,
								Util.tryToParseAsNumber(paramValues[0]).orElse(null),
								Util.tryToParseAsNumber(paramValues[1]).orElse(null)));
						break;
					default:
						filters.add(new TermResultFilter(field, split(paramValue, VALUE_DELIMITER))
								.setFilterOnId(isIdFilter));
				}
			}
		}

		return filters;
	}

	/**
	 * Parses the sorting parameter into a list of enriched Sorting objects.
	 * 
	 * @param paramValue
	 *        the sorting parameter value
	 * @param fields
	 *        the field configuration
	 * @return list of validated sortings
	 */
	public static List<Sorting> parseSortings(String paramValue, FieldConfigIndex fields) {
		String[] paramValueSplit = split(paramValue, VALUE_DELIMITER);
		List<Sorting> sortings = new ArrayList<>(paramValueSplit.length);
		for (String rawSortValue : paramValueSplit) {
			String fieldName = rawSortValue;
			SortOrder sortOrder = SortOrder.ASC;

			if (rawSortValue.startsWith(SORT_DESC_PREFIX)) {
				fieldName = rawSortValue.substring(1);
				sortOrder = SortOrder.DESC;
			}

			if (fields.getMatchingField(fieldName, FieldUsage.SORT).isPresent()) {
				sortings.add(new Sorting(null, fieldName, sortOrder, true, null));
			}
		}
		return sortings;
	}

}
