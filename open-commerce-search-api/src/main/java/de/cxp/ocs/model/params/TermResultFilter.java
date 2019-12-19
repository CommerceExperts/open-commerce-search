package de.cxp.ocs.model.params;

import lombok.Data;

/**
 * used for exact filtering of one or more values.
 */
@Data
public class TermResultFilter implements ResultFilter {

	final String _type = "TermResultFilter";

	private final String field;

	private final String[] values;

	public TermResultFilter(String name, String... inputValues) {
		field = name;
		values = inputValues;
	}

	public Object getSingleValue() {
		if (values.length > 0) return values[0];
		return null;
	}

	public Object getValue(int index) {
		if (values.length > index) return values[index];
		return null;
	}

}
