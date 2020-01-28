package de.cxp.ocs.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

	public FieldConfiguration addField(Field field) {
		if (fields.put(field.getName(), field) != null) {
			log.warn("overwriting field configuration {}", field.getName());
		}
		return this;
	}

	/**
	 * Returns the first field with {@link FieldType#id}.
	 * 
	 * @return an <code>Optional</code> containing the first found ID field,
	 *         or an empty <code>Optional</code> if no such field could be
	 *         found.
	 */
	public Optional<Field> getIdField() {
		return fields.values().stream().filter(f -> FieldType.id.equals(f.getType())).findFirst();
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
	 * Returns the first field whos source name list contains the passed source
	 * name.
	 * 
	 * @param sourceName
	 *        the source name to check.
	 * @return an optional containing the found field, if any.
	 */
	public Optional<Field> getFieldBySourceName(final String sourceName) {
		return fields.values().stream().filter(f -> f.getSourceNames().contains(sourceName)).findFirst();
	}

	/**
	 * Returns the first field matching the passed type.
	 * 
	 * @param type
	 *        the type of the fields to return.
	 * @return an optional containing the found field, if any.
	 */
	public Optional<Field> getField(final FieldType type) {
		return fields.values().stream().filter(f -> type.equals(f.getType())).findFirst();
	}

	/**
	 * Returns all fields of the specified type.
	 * 
	 * @param type
	 *        the type of the fields to return.
	 * @return a list containing all fields of the specified type.
	 */
	public List<Field> getFieldsByType(@NonNull FieldType type) {
		return fields.values().stream().filter(f -> type.equals(f.getType())).collect(Collectors.toList());
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
