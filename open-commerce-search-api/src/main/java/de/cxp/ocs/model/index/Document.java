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

	public Document putData(String name, Object value) {
		data.put(name, value);
		return this;
	}
}
