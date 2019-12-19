package de.cxp.ocs.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FacetEntry {

	public final String	_type	= "FacetEntry";
	String				key;
	long				docCount;

}
