package de.cxp.ocs.elasticsearch.query.filter;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConstants;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * used for exact filtering of one or more values.
 */
@Accessors(chain = true)
@Data
public class TermResultFilter implements InternalResultFilter {

	private final Field field;

	private final String[] values;

	private boolean filterOnId = false;

	private String fieldPrefix = FieldConstants.TERM_FACET_DATA;

	public TermResultFilter(Field field, String... inputValues) {
		this.field = field;
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
	public boolean isNestedFilter() {
		return true;
	}
}