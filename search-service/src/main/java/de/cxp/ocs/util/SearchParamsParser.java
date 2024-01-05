package de.cxp.ocs.util;

import static de.cxp.ocs.util.DefaultLinkBuilder.SORT_DESC_PREFIX;
import static de.cxp.ocs.util.DefaultLinkBuilder.VALUE_DELIMITER;
import static de.cxp.ocs.util.DefaultLinkBuilder.VALUE_DELIMITER_ENCODED;
import static org.apache.commons.lang3.StringUtils.replace;
import static org.apache.commons.lang3.StringUtils.split;
import static org.apache.commons.lang3.StringUtils.splitPreserveAllTokens;

import java.util.*;
import java.util.Map.Entry;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.PathResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilter;
import de.cxp.ocs.model.params.ArrangedSearchQuery;
import de.cxp.ocs.model.params.SearchQuery;
import de.cxp.ocs.model.result.SortOrder;
import de.cxp.ocs.model.result.Sorting;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class to parse search parameters
 */
@Slf4j
public class SearchParamsParser {

	private static final Set<FieldUsage> FILTERABLE_FIELD_USAGES = EnumSet.of(FieldUsage.FACET, FieldUsage.FILTER);

	/**
	 * Max result count as defined by default Elasticsearch setting 'max_result_window'
	 */
	public final static int MAX_RESULT_COUNT = 10000;

	public final static String	ID_FILTER_SUFFIX		= ".id";
	public final static String	NEGATE_FILTER_PREFIX	= "!";

	public static InternalSearchParams extractInternalParams(SearchQuery searchQuery, Map<String, String> filters, SearchContext searchContext) {
		final InternalSearchParams parameters = new InternalSearchParams();
		parameters.limit = searchQuery.limit;
		parameters.offset = searchQuery.offset;
		if (parameters.limit > MAX_RESULT_COUNT) {
			parameters.limit = MAX_RESULT_COUNT;
		}
		if (parameters.limit + parameters.offset > MAX_RESULT_COUNT) {
			parameters.offset = MAX_RESULT_COUNT - parameters.limit;
		}

		parameters.withFacets = searchQuery.withFacets;
		parameters.userQuery = searchQuery.q;

		if (searchQuery.sort != null) {
			parameters.sortings = parseSortings(searchQuery.sort, searchContext.getFieldConfigIndex());
		}
		parameters.filters = parseFilters(filters, searchContext.getFieldConfigIndex(), searchContext.config.getLocale());

		Map<String, String> customParams = filters == null ? Collections.emptyMap() : new HashMap<>(filters);
		parameters.filters.forEach(f -> {
			customParams.remove(f.getField().getName());
			customParams.remove(f.getField().getName() + SearchParamsParser.ID_FILTER_SUFFIX);
		});
		parameters.customParams = customParams;

		if (searchQuery instanceof ArrangedSearchQuery) {
			parameters.includeMainResult = ((ArrangedSearchQuery) searchQuery).includeMainResult;
		}

		parameters.trace = Optional.ofNullable(parameters.customParams.get("trace")).map(TraceOptions::parse).orElse(TraceOptions.OFF);

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
	 * @param locale
	 *        locale for filter value normalization
	 * @return validated and enriched filter values for internal usage
	 */
	public static List<InternalResultFilter> parseFilters(Map<String, String> filterValues, FieldConfigIndex fieldConfig, Locale locale) {
		List<InternalResultFilter> filters = filterValues == null ? Collections.emptyList() : new ArrayList<>();

		if (filterValues != null) {
			for (Entry<String, String> p : filterValues.entrySet()) {
				// special handling for spring: filters maps contains all
				// parameters
				// and the mapping result objects
				if (!(p.getValue() instanceof String)) continue;

				String paramName = p.getKey();
				String paramValue = p.getValue();

				parseSingleFilter(paramName, paramValue, fieldConfig, locale).ifPresent(filters::add);
			}
		}

		return filters;
	}

	public static Optional<InternalResultFilter> parseSingleFilter(final String paramName, final String paramValue, FieldConfigIndex fieldConfig, Locale locale) {
		final boolean isIdFilter = paramName.endsWith(ID_FILTER_SUFFIX);
		final String fieldName = isIdFilter ? paramName.substring(0, paramName.length() - 3) : paramName;
		return fieldConfig.getMatchingField(fieldName, paramValue, FILTERABLE_FIELD_USAGES)
				.map(f -> {
					try {
						return toInternalFilter(f, paramValue, locale, isIdFilter);
					}
					catch (IllegalArgumentException iae) {
						log.error("Ignoring invalid filter parameter {}={}", paramName, paramValue);
						return null;
					}
				});
	}

	public static InternalResultFilter toInternalFilter(Field field, String paramValue, Locale locale, boolean isIdFilter) {
		boolean negate = paramValue.startsWith(NEGATE_FILTER_PREFIX);
		if (negate) {
			paramValue = paramValue.substring(NEGATE_FILTER_PREFIX.length());
		}

		String[] paramValues = decodeValueDelimiter(split(paramValue, VALUE_DELIMITER));

		// if there are single values with the negate-filter-prefix, we handle them here
		paramValues = fixMultiNegationPrefixes(paramValues, negate);

		return toInternalFilter(field, paramValues, locale, isIdFilter, negate);
	}

	public static InternalResultFilter toInternalFilter(Field field, String[] paramValues, Locale locale, boolean isIdFilter, boolean negate) {
		InternalResultFilter internalFilter;
		switch (field.getType()) {
			case CATEGORY:
				internalFilter = new PathResultFilter(field, paramValues)
						.setFieldPrefix(FieldConstants.PATH_FACET_DATA)
						.setFilterOnId(isIdFilter)
						.setNegated(negate);
				break;
			case NUMBER:
				internalFilter = parseNumberFilter(field, paramValues)
						.setNegated(negate);
				break;
			default:
				if (isIdFilter && !field.hasUsage(FieldUsage.FACET)) {
					// not a warning, because this is a request failure. A 40x failure would be more suitable, but we
					// avoid that here
					log.info("Unsupported ID Filter: {}. ID filtering not possible for fields with usage FILTER", field.getName());
					internalFilter = null;
				}
				else {
					internalFilter = new TermResultFilter(locale, field, paramValues)
						.setFilterOnId(isIdFilter)
						.setNegated(negate);
				}
		}
		return internalFilter;
	}

	private static String[] fixMultiNegationPrefixes(String[] paramValues, boolean negatedFilter) {
		int fixParamValues = 0;
		for (int i = 0; i < paramValues.length; i++) {
			if (!paramValues[i].startsWith(NEGATE_FILTER_PREFIX)) continue;

			if (negatedFilter) {
				// everything is negated, no need to negate the single value
				paramValues[i] = paramValues[i].substring(NEGATE_FILTER_PREFIX.length());
			}
			else {
				// since at least the first value is an include filter, everything is considered as excluded
				// anyways, however for multi-value fields that would semanticaly make sense (i.e. get all
				// products that are available in "red" but not in "black", but it's not supported unless there
				// is a valid usecase for that)
				paramValues[i] = null;
				fixParamValues++;
			}
		}

		// performance optimized version of the one in PR #74
		// Arrays.stream(paramValues).filter(Objects::nonNull).toArray(String[]::new);
		if (fixParamValues > 0) {
			String[] fixedValues = new String[paramValues.length - fixParamValues];
			int insertIndex = 0;
			for (int i = 0; i < paramValues.length; i++) {
				if (paramValues[i] != null) {
					fixedValues[insertIndex++] = paramValues[i];
				}
			}
			return fixedValues;
		}
		else {
			return paramValues;
		}
	}

	private static NumberResultFilter parseNumberFilter(Field field, String[] paramValues) {
		if (paramValues.length == 1) {

			// Fallback logic to allow numeric filter values
			// separated by dash, e.g. "50 - 100"
			// however this is error prone, because dash is
			// also used as minus for negative values. In
			// such
			// case this will simply fail
			paramValues = splitPreserveAllTokens(paramValues[0], '-');
			// if a single value is provided, use it as a min and max, so products with exactly that value are returned
			// (good for numeric flag fields with 0 and 1)
			if (paramValues.length == 1) {
				paramValues = new String[] { paramValues[0], paramValues[0] };
			}
			if (paramValues.length != 2) {
				throw new IllegalArgumentException("unexpected numeric filter value: " + paramValues[0]);
			}
		}
		Number min = Integer.MAX_VALUE;
		Number max = Integer.MIN_VALUE;
		for (String value : paramValues) {
			value = value.trim();
			if (value.isEmpty()) continue;

			Number numValue = Util.tryToParseAsNumber(value).orElseThrow(IllegalArgumentException::new);
			if (numValue.doubleValue() < min.doubleValue()) {
				min = numValue;
			}
			if (numValue.doubleValue() > max.doubleValue()) {
				max = numValue;
			}
		}
		return new NumberResultFilter(field, min, max);
	}

	private static String[] decodeValueDelimiter(String[] split) {
		for (int i = 0; i < split.length; i++) {
			split[i] = replace(split[i], VALUE_DELIMITER_ENCODED, VALUE_DELIMITER);
		}
		return split;
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
