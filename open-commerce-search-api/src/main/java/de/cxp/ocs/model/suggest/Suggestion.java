package de.cxp.ocs.model.suggest;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Suggestion {

	@NonNull
	@Schema(description = "The phrase that is suggested and/or used as suggestion label.")
	public String phrase;

	@Schema(
			description = "Optional type of that suggestion. Should be different for the different kind of suggested data. "
					+ "Default: 'keyword'",
			example = "keyword, brand, category, product")
	public String type = "keyword";

	@Schema(description = "arbitrary payload attached to that suggestion. Default: null")
	Map<String, Object> payload;
}
