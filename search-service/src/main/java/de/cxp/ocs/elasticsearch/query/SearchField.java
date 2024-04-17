package de.cxp.ocs.elasticsearch.query;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@AllArgsConstructor
@RequiredArgsConstructor
public class SearchField {

	@Getter
	@NonNull
	private final Field field;

	private String subField;

	public String getFullName() {
		String suffix = subField == null || subField.isBlank() ? "" : "." + subField;
		return FieldConstants.SEARCH_DATA + "." + field.getName() + suffix;
	}
}
