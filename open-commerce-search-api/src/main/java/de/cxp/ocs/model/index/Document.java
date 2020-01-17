package de.cxp.ocs.model.index;

import java.util.HashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A simple 'data record' similar to a row inside a CSV file that contains any
 * data relevant for search. The single field types and conversions are part of
 * the according service configuration.
 */
@Data
@Accessors(chain = true)
@RequiredArgsConstructor
public class Document {

	@NonNull
	String id;

	@Schema(
			anyOf = {
					Integer.class,
					Long.class,
					Double.class,
					String.class,
					Attribute.class,
					Integer[].class,
					Long[].class,
					Double[].class,
					String[].class,
					Attribute[].class
			})
	Map<String, Object> data = new HashMap<>();

	public Document() {}

	public Document set(String name, String... values) {
		data.put(name, values.length == 1 ? values[0] : values);
		return this;
	}

	public Document set(String name, long... values) {
		data.put(name, values.length == 1 ? values[0] : values);
		return this;
	}

	public Document set(String name, int... values) {
		data.put(name, values.length == 1 ? values[0] : values);
		return this;
	}

	public Document set(String name, double... values) {
		data.put(name, values.length == 1 ? values[0] : values);
		return this;
	}

	public Document set(String name, Attribute... values) {
		data.put(name, values.length == 1 ? values[0] : values);
		return this;
	}
}
