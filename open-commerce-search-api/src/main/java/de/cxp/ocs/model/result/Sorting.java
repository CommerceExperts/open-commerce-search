package de.cxp.ocs.model.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Sorting {

	public String		field;

	public SortOrder	sortOrder;

	/**
	 * Is set to true, if this sorting is active in the current result.
	 */
	public boolean      isActive = false;
	
	/**
	 * URL conform query parameters, that has to be used to activate that sort
	 * option.
	 */
	@Schema(format = "URI")
	public String		link;
}
