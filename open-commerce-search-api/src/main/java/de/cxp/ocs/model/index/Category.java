package de.cxp.ocs.model.index;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Category {

	@Schema(description = "Optional ID for a consistent filtering")
	public String id;

	@Schema(required = true)
	public String name;

	public static Category of(String name) {
		return new Category(null, name);
	}

}
