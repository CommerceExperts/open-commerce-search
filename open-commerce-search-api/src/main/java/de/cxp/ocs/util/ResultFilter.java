package de.cxp.ocs.util;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultFilter {

	String			field;
	List<String>	values;

	public String getStringRepresentation() {
		StringBuilder filterAsString = new StringBuilder(field).append(":");
		if (values != null) {
			boolean appended = false;
			for (String v : values) {
				if (v != null) {
					if (appended) filterAsString.append(',');
					filterAsString.append(
							StringUtils.replaceEach(v,
									new String[] { ":", "," },
									new String[] { "%3A", "%2C" }));
					appended = true;
				}
			}
		}
		return filterAsString.toString();
	}

	public static ResultFilter parse(String value) {
		String[] name_values = StringUtils.split(value, ":", 2);
		String[] values = StringUtils.split(name_values[1], ",");

		if (name_values[1].indexOf('%') != -1) {
			for (int i = 0; i < values.length; i++) {
				values[i] = StringUtils.replaceEach(values[i],
						new String[] { "%3A", "%2C" },
						new String[] { ":", "," });
			}
		}

		return new ResultFilter(name_values[0], Arrays.asList(values));
	}
	
}