package de.cxp.ocs.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FacetEntry {

	String	key;
	long	docCount;

}
