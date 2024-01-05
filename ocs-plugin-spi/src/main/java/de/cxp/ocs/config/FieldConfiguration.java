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

	@NonNull
	private final List<Field> dynamicFields = new ArrayList<>();

	public boolean useDefaultConfig;

	/**
	 * Add explicit field configuration. Defines how data fields are indexed.
	 * 
	 * @param field
	 *        field to add
	 * @return changed configuration
	 */
	public FieldConfiguration addField(Field field) {
		fields.put(field.getName(), field);
		return this;
	}

	/**
	 * <p>
	 * Add field definition, that is used as template for unknown fields.
	 * </p>
	 * <p>
	 * For dynamic fields the properties 'name', 'sourceNames' and 'type' have a
	 * special meaning:
	 * </p>
	 * <ul>
	 * <li>name: is actually ignored, but if it is set to 'attribute', that
	 * dynamic field is only applied to attributes (hack)</li>
	 * <li>*sourceNames*: Each source name is used as a regular expressions for
	 * the unknown fields. If it's not specified, the field name is irrelevant
	 * and only the other criterion must match.</li>
	 * <li>type: can be 'string' or 'number' and that dynamic field will only be
	 * used, if the data field is of that particular type.</li>
	 * </ul>
	 * <p>
	 * The order of dynamic fields matter. Unknown fields are checked against
	 * them in the defined order. Once a field configuration is derived from a
	 * dynamic field, the dynamic fields are not considered anymore for the same
	 * data field.
	 * </p>
	 * 
	 * @param dynamicField
	 *        field config as template
	 * @return changed configuration
	 */
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
