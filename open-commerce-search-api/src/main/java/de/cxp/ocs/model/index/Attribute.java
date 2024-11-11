package de.cxp.ocs.model.index;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;

@Schema(
		name = "Attribute",
		description = "Rich model that can be used to represent a document's or product's attribute."
				+ " The attribute 'name' should be a URL friendly identifier for that attribute (rather maxSpeed than 'Max Speed')."
				+ " It will be used as filter parameter laster."
				+ " If the attribute 'code' is provieded, it can be used for consistent filtering, even if the value name should change."
				+ " The values are used to produce nice facets or if used for search, they will be added to the searchable content.",
		example = "{\"name\": \"color\", \"value\": \"red\", \"code\": \"ff0000\"}")
@Accessors(chain = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Attribute {
	
	@Schema(
			required = true,
			description = "The name SHOULD be URL friendly identifier for the attribute,"
					+ " since it could be used to build according filter parameters.",
			pattern = "[A-Za-z0-9\\-_.]")
	@NonNull
	public String name;

	@Schema(
			description = "Optional: code is considered as ID of the attribute value, e.g. \"FF0000\" for color",
			pattern = "[A-Za-z0-9\\-_.]")
	public String code;

	@Schema(
			required = true,
			description = "Human readable representation of that attribute, e.g. 'Red' for the attribute 'Color'."
					+ " Values can be numeric and ")
	@NonNull
	public String value;

	public static Attribute of(String name, String value) {
		return new Attribute(name, null, value);
	}

}