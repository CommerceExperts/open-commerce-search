package de.cxp.ocs.elasticsearch.query.filter;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.config.Field;
import lombok.Getter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class PathResultFilter extends TermResultFilter {

	public static final char PATH_SEPARATOR = '/';

	@Getter
	private List<String[]> filterPaths;

	@Getter
	private String[] leastPathValues;

	public PathResultFilter(Field field, String[] inputValues) {
		super(field, inputValues);

		filterPaths = this.getValuesAsList().stream()
				.map(s -> StringUtils.split(s, PATH_SEPARATOR))
				.collect(Collectors.toList());

		leastPathValues = new String[filterPaths.size()];
		for (int i = 0; i < leastPathValues.length; i++) {
			String[] filterPath = filterPaths.get(i);
			leastPathValues[i] = filterPath[filterPath.length - 1];
		}
	}

}
