package de.cxp.ocs.model.params;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Sorting {

	public String		field;
	public SortOrder	sortOrder;
}
