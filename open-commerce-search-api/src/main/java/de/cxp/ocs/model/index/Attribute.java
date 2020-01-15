package de.cxp.ocs.model.index;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Data
@NoArgsConstructor
public class Attribute {
	
	public String id;

	public String name;
	
	public Attribute(String name, String id) {
		this.name = name;
		this.id = id;
	}
	
	public static Attribute of(String name) {
		return new Attribute(name, name);
	}

	public static Attribute of(String name, String id) {
		return new Attribute(name, id);
	}
}
