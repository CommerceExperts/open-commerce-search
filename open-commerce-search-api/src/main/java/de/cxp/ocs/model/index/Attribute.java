package de.cxp.ocs.model.index;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Schema(
		description = "Rich model that can be used to represent a document or product attribute."
				+ " If 'id' and/or 'code' are provieded, these can be used for consistent filtering, even if the label and values are changing."
				+ " The label and the values will be used used to produce nice facets or if used for search, they will be added to the searchable content.",
		example = "{\"id\": \"a.maxSpeed\", \"label\": \"Max Speed\", \"value\": \"230 km/h\", \"code\": 230}")
@Accessors(chain = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
public class Attribute {
	
	@Schema(
			description = "Optional: Static ID of that attribute."
					+ " The id SHOULD be URL friendly, since it could be used to build according filter parameters."
					+ " If not set, the label could be used for parameter building.",
			pattern = "[A-Za-z0-9\\-_.]")
	public String id;

	@Schema(description = "Human readable name of the attribute, e.g. 'Color' or 'Max. Speed in km/h'")
	@NonNull
	public String label;
	
	@Schema(
			description = "Optional: code that represents that attribute value, e.g. \"FF0000\" for color",
			pattern = "[A-Za-z0-9\\-_.]")
	public String code;

	@Schema(description = "Human readable representation of that attribute, e.g. 'Red' for the attribute 'Color'")
	@NonNull
	public String value;

	public static Attribute of(String label, String value) {
		return new Attribute(label, value);
	}

}