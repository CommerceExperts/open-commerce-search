package de.cxp.ocs.util;

import static de.cxp.ocs.util.SortOrder.*;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Sorting {

	public String		field;
	public SortOrder	sortOrder;

	public String getStringRepresentation() {
		return (sortOrder.equals(DESC) ? "-" : "") + field;
	}

	public static Sorting fromStringRepresentation(String sortSpec) {
		boolean isDesc = sortSpec.startsWith("-");
		return new Sorting(isDesc ? sortSpec.substring(1) : sortSpec, isDesc ? DESC : ASC);
	}
}
