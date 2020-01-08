package de.cxp.ocs.model.index;

import lombok.Data;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Data
public class Attribute {
	
	@Setter
	private String id;

	@Setter
	private String name;
	
	public Attribute() {}
	
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
