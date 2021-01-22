package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
		if (fields.put(field.getName(), field) != null) {
			log.warn("overwriting field configuration {}", field.getName());
		}
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
	 * Returns the first field matching the passed type.
	 * 
	 * @param type
	 *        the type of the fields to return.
	 * @return an optional containing the found field, if any.
	 * @deprecated This method is OK for a single usage, but it's not efficient
	 *             to use it regularly in the code. Consider storing and
	 *             according index instead.
	 */
	public Optional<Field> getField(final FieldType type) {
		return fields.values().stream().filter(f -> type.equals(f.getType())).findFirst();
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
