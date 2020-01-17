package de.cxp.ocs.elasticsearch.query.filter;

import java.util.Arrays;
import java.util.List;

import lombok.Data;

/**
 * used for exact filtering of one or more values.
 */
@Data
public class TermResultFilter implements InternalResultFilter {

	private final String field;

	private final List<String> values;

	public TermResultFilter(String name, String... inputValues) {
		field = name;
		values = Arrays.asList(inputValues);
	}

	public TermResultFilter(String name, List<String> inputValues) {
		field = name;
		values = inputValues;
	}

	public Object getSingleValue() {
		if (values.size() > 0) return values.get(0);
		return null;
	}

	public Object getValue(int index) {
		if (values.size() > index) return values.get(index);
		return null;
	}

}