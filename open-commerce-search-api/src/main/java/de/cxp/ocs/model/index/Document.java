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

	/**
	 * <pre>
	 * type: object
	 * anyOf:
	 * - type: string
	 * - type: number
	 * - type: boolean
	 * - $ref: '#/components/schemas/Attribute'
	 * - type: array
	 * _ items:
	 * ___ oneOf:
	 * _____ - type: string
	 * _____ - type: number
	 * _____ - type: boolean
	 * _____ - $ref: '#/components/schemas/Attribute'
	 * </pre>
	 */
	private Map<String, Object> data = new HashMap<>();

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
