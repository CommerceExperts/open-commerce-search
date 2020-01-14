package de.cxp.ocs.util;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.NumberResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.PathResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilter;

public class SearchParamsParser {

	public static final String	PARAM_NAME_DELIMITER			= "_";
	public static final String	PARAM_NUMBER_VALUE_DELIMITER	= "_";
	public static final String	PARAM_VALUE_DELIMITER			= "~";

	public static final String	FILTER_PREFIX	= "f" + PARAM_NAME_DELIMITER;
	public static final String	SORT_PREFIX		= "s" + PARAM_NAME_DELIMITER;

	/**
	 * @throws IllegalArgumentException
	 *         if a parameter has an unexpected value
	 * @param params
	 * @return
	 */
	public static InternalSearchParams parseParams(Map<String, Object> params, Map<String, Field> fields) {
		final InternalSearchParams searchParams = new InternalSearchParams();
		if (params == null) return searchParams;

		params.computeIfPresent("size", (k, size) -> searchParams.limit = Integer.parseInt(size.toString()));
		params.computeIfPresent("limit", (k, size) -> searchParams.limit = Integer.parseInt(size.toString()));
		params.computeIfPresent("offset", (k, offset) -> searchParams.offset = Integer.parseInt(offset.toString()));

		for (Entry<String, Object> p : params.entrySet()) {
			String paramName = p.getKey();
			String paramValue = String.valueOf(p.getValue());

			if (paramName.equals(FILTER_PREFIX + FieldConstants.CATEGORY_FACET_DATA)) {
				searchParams.filters.add(new PathResultFilter(FieldConstants.CATEGORY_FACET_DATA, Arrays.asList(split(paramValue, ','))));
			}
			else if (paramName.startsWith(FILTER_PREFIX)) {
				String[] paramNameSplit = org.apache.commons.lang3.StringUtils.split(paramName, PARAM_NAME_DELIMITER, 2);
				String filterName = paramNameSplit[1];
				Field field = fields.get(filterName);
				if (field == null) field = new Field(filterName);
				searchParams.withFilter(getResultFilter(field, paramValue));
			}
			else if (paramName.startsWith(SORT_PREFIX)) {
				searchParams.withSorting(getSorting(paramName, paramValue));
			}
		}

		return searchParams;
	}

	private static InternalResultFilter getResultFilter(Field field, String paramValue) {
		if (FieldType.number.equals(field.getType())) {
			String[] paramValueSplit = splitPreserveAllTokens(paramValue, PARAM_NUMBER_VALUE_DELIMITER);
			if (paramValueSplit.length != 2) throw new IllegalArgumentException("unexpected numeric filter value: "
					+ paramValue);
			try {
				return new NumberResultFilter(
						field.getName(),
						Util.tryToParseAsNumber(paramValueSplit[0]).orElse(null),
						Util.tryToParseAsNumber(paramValueSplit[1]).orElse(null));
			}
			catch (IllegalArgumentException iae) {
				throw new IllegalArgumentException("unexpected numeric filter value: " + paramValue, iae);
			}
		}
		else {
			String[] paramValueSplit = split(paramValue, PARAM_VALUE_DELIMITER);
			return new TermResultFilter(field.getName(), paramValueSplit);
		}
	}

	private static Sorting getSorting(String paramName, String paramValue) {
		String[] paramNameSplit = split(paramName, PARAM_NAME_DELIMITER, 2);
		Sorting sorting = new Sorting(paramNameSplit[1], SortOrder.valueOf(paramValue.toUpperCase()));
		return sorting;
	}
}
