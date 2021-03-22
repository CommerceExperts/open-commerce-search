package de.cxp.ocs.config;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A interface that is implemented by OCS, to give quick and easy access to the
 * configured fields.
 */
public interface FieldConfigAccess {

	/**
	 * Get field by unique field name.
	 * 
	 * @param fieldName
	 * @return
	 */
	Optional<Field> getField(String fieldName);

	/**
	 * Get field configuration that should be used for the "Categories" property
	 * of the documents.
	 * 
	 * @param usage
	 * @return
	 */
	Optional<Field> getPrimaryCategoryField();

	/**
	 * Get all fields that have the specified usage configured.
	 * 
	 * @param usage
	 * @return
	 */
	Map<String, Field> getFieldsByUsage(FieldUsage usage);

	/**
	 * get all fields that have the specified field type configured.
	 * 
	 * @param type
	 * @return
	 */
	Map<String, Field> getFieldsByType(FieldType type);

	/**
	 * Get field with that name and the specified usage. If this does not exist,
	 * a empty result will be returned.
	 * 
	 * @param fieldName
	 * @param usage
	 * @return
	 */
	Optional<Field> getMatchingField(String fieldName, FieldUsage usage);

	/**
	 * Get all fields that have the the specified name as field-name or
	 * source-field. If source-fields are not given at initialization (such at
	 * the search service), this function works similar to
	 * {@code getField(String)}
	 * 
	 * @param fieldName
	 * @return
	 */
	Set<Field> getMatchingFields(String name);

	/**
	 * Similar to {@code getMatchingField(String)} but additionally tries to
	 * generate a field configuration based on the dynamic fields. If no dynamic
	 * fields are configured (such as at the search-service), no field configs
	 * are generated.
	 * 
	 * @param fieldName
	 * @param value
	 * @return
	 */
	Set<Field> getMatchingFields(String fieldName, Object value);

}
