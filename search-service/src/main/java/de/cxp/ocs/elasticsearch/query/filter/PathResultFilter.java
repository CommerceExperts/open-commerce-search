package de.cxp.ocs.elasticsearch.query.filter;

import java.util.List;

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


}