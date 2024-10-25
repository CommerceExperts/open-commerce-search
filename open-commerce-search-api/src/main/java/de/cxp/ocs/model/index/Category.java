package de.cxp.ocs.model.index;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Schema(
		name = "Category",
		description = "Model that represents a single category inside a category path.",
		example = "{\"id\": \"7001\", \"name\": \"Sale\"}")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Category {

	@Schema(description = "Optional ID for a consistent filtering")
	public String id;

	@Schema(required = true, description = "actual category name")
	public String name;

	public static Category of(String name) {
		return new Category(null, name);
	}

	public String toString() {
		return id == null ? name : name + ":" + id;
	}
}
