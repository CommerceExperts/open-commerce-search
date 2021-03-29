package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class FieldConfiguration {

	@NonNull
	private final Map<String, Field> fields = new LinkedHashMap<>();

	/**
	 * for dynamic field names, the sourceNames are used as regular expressions.
	 */
	@NonNull
	private final List<Field> dynamicFields = new ArrayList<>();

	public FieldConfiguration addField(Field field) {
		fields.put(field.getName(), field);
		return this;
	}

	public FieldConfiguration addDynamicField(Field dynamicField) {
		if (dynamicField != null) dynamicFields.add(dynamicField);
		return this;
	}

	/**
	 * Gets a Field by it's name.
	 * 
	 * @param name
	 *        the name of the field.
	 * @return the configured field or <code>null</code> if no field with that
	 *         name exists in the configuration.
	 */
	public Field getField(final String name) {
		return fields.get(name);
	}

	/**
	 * Checks weather a field with the passed name exists or not.
	 * 
	 * @param name
	 *        the name to check.
	 * @return <code>true</code> if a field with that name exists,
	 *         <code>false</code> otherwise.
	 */
	public boolean hasField(final String name) {
		return fields.containsKey(name);
	}
}
