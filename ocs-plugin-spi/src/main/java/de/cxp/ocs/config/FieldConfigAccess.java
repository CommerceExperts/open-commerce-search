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
	 *        unique field name
	 * @return optional matching field
	 */
	Optional<Field> getField(String fieldName);

	/**
	 * Get field configuration that should be used for the "Categories" property
	 * of the documents.
	 * 
	 * @return optional matching field
	 */
	Optional<Field> getPrimaryCategoryField();

	/**
	 * Get all fields that have the specified usage configured.
	 * 
	 * @param usage
	 *        filter criterion
	 * @return matching fields
	 */
	Map<String, Field> getFieldsByUsage(FieldUsage usage);

	/**
	 * get all fields that have the specified field type configured.
	 * 
	 * @param type
	 *        filter criterion
	 * @return matching fields
	 */
	Map<String, Field> getFieldsByType(FieldType type);

	/**
	 * Get field with that name and the specified usage. If this does not exist,
	 * a empty result will be returned.
	 * 
	 * @param fieldName
	 *        could match any source-field name
	 * @param usage
	 *        filter criterion
	 * @return optional matching field
	 */
	Optional<Field> getMatchingField(String fieldName, FieldUsage usage);

	/**
	 * Get all fields that have the the specified name as field-name or
	 * source-field. If source-fields are not given at initialization (such at
	 * the search service), this function works similar to
	 * {@code getField(String)}
	 * 
	 * @param name
	 *        could match any source-field name
	 * @return matching fields
	 */
	Set<Field> getMatchingFields(String name);

	/**
	 * Similar to {@code getMatchingField(String)} but additionally tries to
	 * generate a field configuration based on the dynamic fields. If no dynamic
	 * fields are configured (such as at the search-service), no field configs
	 * are generated.
	 * 
	 * @param fieldName
	 *        could match any source-field
	 * @param value
	 *        filter criterion
	 * @return matching fields
	 */
	Set<Field> getMatchingFields(String fieldName, Object value);

}
