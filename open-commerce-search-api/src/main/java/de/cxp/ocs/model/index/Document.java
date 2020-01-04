package de.cxp.ocs.model.index;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@RequiredArgsConstructor
public class Document {

	@NonNull
	String id;

	Map<String, Object> data = new HashMap<>();

	public Document() {}

	public Document set(String name, String value) {
		data.put(name, value);
		return this;
	}

	public Document set(String name, int value) {
		data.put(name, value);
		return this;
	}

	public Document set(String name, double value) {
		data.put(name, value);
		return this;
	}

	public Document set(String name, Attribute... values) {
		data.put(name, values);
		return this;
	}
}
