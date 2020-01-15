package de.cxp.ocs.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SortOption {

	public String		field;

	public SortOrder	sortOrder;

	/**
	 * URL conform query parameters, that has to be used to
	 * activate that sort option.
	 */
	public String		link;
}
