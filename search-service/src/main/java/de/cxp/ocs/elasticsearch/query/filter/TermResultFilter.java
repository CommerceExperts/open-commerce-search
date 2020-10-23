package de.cxp.ocs.elasticsearch.query.filter;

import de.cxp.ocs.config.FieldConstants;
import lombok.Data;

/**
 * used for exact filtering of one or more values.
 */
@Data
public class TermResultFilter implements InternalResultFilter {

	private final String field;

	private final String[] values;

	public TermResultFilter(String name, String... inputValues) {
		field = name;
		values = inputValues;
	}

	public String getSingleValue() {
		if (values.length > 0) return values[0];
		return null;
	}

	public String getValue(int index) {
		if (values.length > index) return values[index];
		return null;
	}

	@Override
	public String getFieldPrefix() {
		return FieldConstants.TERM_FACET_DATA;
	}

	@Override
	public boolean isNestedFilter() {
		return true;
	}
}