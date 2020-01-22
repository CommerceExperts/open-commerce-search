package de.cxp.ocs.model.index;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Category {

	public String id;

	public String name;

	public static Category of(String name) {
		return new Category(null, name);
	}

}
