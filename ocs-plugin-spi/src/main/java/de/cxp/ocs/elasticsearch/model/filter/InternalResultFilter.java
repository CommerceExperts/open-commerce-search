package de.cxp.ocs.elasticsearch.model.filter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.cxp.ocs.config.Field;

public interface InternalResultFilter {

	Field getField();

	boolean isFilterOnId();

	boolean isNegated();

	String getFieldPrefix();

	boolean isNestedFilter();

	String[] getValues();

	void appendFilter(InternalResultFilter sibling);

	static String[] unifiyValues(String[] values1, String[] values2) {
		if (values1 == null || values1.length == 0) return values2 == null ? new String[0] : values2;
		if (values2 == null || values2.length == 0) return values1;

		Set<String> merged = new HashSet<>(Arrays.asList(values1));
		merged.addAll(Arrays.asList(values2));
		return merged.toArray(String[]::new);
	}

}