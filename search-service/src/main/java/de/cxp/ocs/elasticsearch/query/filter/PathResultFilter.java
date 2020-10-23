package de.cxp.ocs.elasticsearch.query.filter;

import java.util.List;

import de.cxp.ocs.config.FieldConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
public class PathResultFilter implements InternalResultFilter {

	@NonNull
	private String field;

	@NonNull
	private List<String> path;

	@Override
	public String[] getValues() {
		return path.toArray(new String[path.size()]);
	}

	@Override
	public String getFieldPrefix() {
		return FieldConstants.CATEGORY_FACET_DATA;
	}

	@Override
	public boolean isNestedFilter() {
		return false;
	}
}