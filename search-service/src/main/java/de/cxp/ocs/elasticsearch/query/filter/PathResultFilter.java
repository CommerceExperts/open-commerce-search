package de.cxp.ocs.elasticsearch.query.filter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class PathResultFilter implements InternalResultFilter {

	public static final char PATH_SEPARATOR = '/';

	@Getter
	private final Field field;

	@Getter
	private final String[] values;

	@Getter
	private List<String[]> filterPaths;

	@Getter
	private String[] leastPathValues;

	@Getter
	@Setter
	private boolean isFilterOnId = false;

	@Getter
	@Setter
	private boolean isNegated = false;

	@Getter
	@Setter
	private String fieldPrefix;

	public PathResultFilter(Field field, String... inputValues) {
		this.field = field;
		values = inputValues;
		filterPaths = Arrays.asList(inputValues).stream()
				.map(s -> StringUtils.split(s, PATH_SEPARATOR))
				.collect(Collectors.toList());

		leastPathValues = new String[filterPaths.size()];
		for (int i = 0; i < leastPathValues.length; i++) {
			String[] filterPath = filterPaths.get(i);
			leastPathValues[i] = filterPath[filterPath.length - 1];
		}
	}

	@Override
	public boolean isNestedFilter() {
		return fieldPrefix != null;
	}


}
