package de.cxp.ocs.model.params;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
public class PathResultFilter implements ResultFilter {

	@NonNull
	private String field;

	@NonNull
	private String[] path;

}