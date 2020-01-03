package de.cxp.ocs.util;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;

@Data
public class ResultFilter {

	String			field;
	List<String>	values;

	public String getStringRepresentation() {
		StringBuilder filterAsString = new StringBuilder(field).append(":");
		if (values != null) {
			for (String v : values) {
				if (v != null) {
					filterAsString.append(
							StringUtils.replaceEach(v,
									new String[] { ":", "," },
									new String[] { "%3A", "%2C" }));
				}
			}
		}
		return filterAsString.toString();
	}

}