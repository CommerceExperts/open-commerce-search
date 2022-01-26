package de.cxp.ocs.elasticsearch.query.filter;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * used for exact filtering of one or more values.
 */
@Accessors(chain = true)
@Data
public class TermResultFilter implements InternalResultFilter {

	private final Field field;

	private final List<String> valuesAsList = new ArrayList<>();

	private boolean filterOnId = false;

	private String fieldPrefix = FieldConstants.TERM_FACET_DATA;

	public TermResultFilter(Field field, String... inputValues) {
		this.field = field;

		for (String singleValue : inputValues) valuesAsList.add(singleValue);
	}

	public String getSingleValue() {
		if (valuesAsList.size() > 0) return valuesAsList.get(0);
		return null;
	}

	public String getValue(int index) {
		if (valuesAsList.size() > index) return valuesAsList.get(index);
		return null;
	}

	@Override
	public boolean isNestedFilter() {
		return true;
	}

	@Override
	public String[] getValues(){
		return valuesAsList.stream().toArray(String[]::new);
	}
}