package de.cxp.ocs.config;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.google.common.base.Predicates;

import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.util.Util;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Special class that cares about resolving the correct field configuration,
 * especially for "the category field" and dynamic fields.
 */
@Slf4j
public final class FieldConfigIndex implements FieldConfigAccess {

	@AllArgsConstructor
	static class DynamicFieldConfig {

		Predicate<String>	fieldNamePredicate;
		Predicate<Object>	valuePredicate;
		Field				fieldConfig;

		public boolean matches(String fieldName, Object value) {
			return (fieldNamePredicate == null || fieldNamePredicate.test(fieldName))
					&& (valuePredicate == null || valuePredicate.test(value));
		}
	}

	@Getter
	private final Map<String, Field> fields = new HashMap<>();

	private final Map<String, List<Field>> fieldsBySource = new HashMap<>();

	private final Map<String, Field> generatedFields = new HashMap<>();

	private final Map<FieldUsage, Map<String, Field>> fieldsByUsage = new HashMap<>();

	private final Map<FieldType, Map<String, Field>> fieldsByType = new HashMap<>();

	@Getter
	private final Optional<Field> primaryCategoryField;

	private final List<DynamicFieldConfig> dynamicFields = new ArrayList<>();

	/**
	 * Constructor of the Field Index that prepares the given field
	 * configurations to match static and dynamic fields.
	 * 
	 * @param fieldConfiguration
	 *        full field configuration
	 */
	public FieldConfigIndex(FieldConfiguration fieldConfiguration) {

		// create name based index of standard fields
		fields.putAll(fieldConfiguration.getFields());

		// also remember all category typed fields
		Map<String, Field> categoryFields = new HashMap<>();
		for (Field field : fieldConfiguration.getFields().values()) {

			if (FieldType.CATEGORY.equals(field.getType())) {
				categoryFields.put(field.getName(), field);
			}

			updateFieldIndexes(field);

			for (String sourceName : field.getSourceNames()) {
				fieldsBySource.computeIfAbsent(sourceName, n -> new ArrayList<Field>(1)).add(field);
			}
		}

		processDynamicFieldConfig(fieldConfiguration);

		primaryCategoryField = determineDefaultCategoryField(categoryFields);
	}

	/**
	 * Add field configuration from another index so that both indexes can be
	 * searched simultaneously.
	 * 
	 * @param fieldConfig additional field config
	 * @throws FieldConfigIncompatibilityException in case the field-config "schema"
	 *                                             has conflicts with the existing
	 *                                             field config
	 */
	public void addFieldConfig(FieldConfiguration fieldConfig) throws FieldConfigIncompatibilityException {
		for (Field newField : fieldConfig.getFields().values()) {

			String fieldName = newField.getName();
			Field existingField = fields.get(fieldName);

			if (existingField == null) {
				if (newField.getUsage().contains(FieldUsage.SORT)) {
					throw new FieldConfigIncompatibilityException(
							"Field " + fieldName + " is used for sorting in only one index.");
				}

				fields.put(fieldName, newField);
			} else {
				if (!existingField.getType().equals(newField.getType())) {
					if (existingField.getUsage().contains(FieldUsage.FACET)
							&& newField.getUsage().contains(FieldUsage.FACET)) {
						throw new FieldConfigIncompatibilityException(
								"Fields with name " + fieldName + " have different types and are used for facetting.");
					}
					if (existingField.getUsage().contains(FieldUsage.SCORE)
							&& newField.getUsage().contains(FieldUsage.SCORE)) {
						throw new FieldConfigIncompatibilityException(
								"Fields with name " + fieldName + " have different types and are used for scoring.");
					}
				}
				if (existingField.getUsage().contains(FieldUsage.SORT) != newField.getUsage()
						.contains(FieldUsage.SORT)) {
					throw new FieldConfigIncompatibilityException(
							"Fields with name " + fieldName + " are used for sorting in only one index.");
				}
			}

			updateFieldIndexes(newField);

			for (String sourceName : newField.getSourceNames()) {
				fieldsBySource.computeIfAbsent(sourceName, n -> new ArrayList<Field>(1)).add(newField);
			}
		}

		processDynamicFieldConfig(fieldConfig);
	}

	public void processDynamicFieldConfig(FieldConfiguration fieldConfiguration) {
		// create index for dynamic fields.
		for (Field dynamicField : fieldConfiguration.getDynamicFields()) {

			Predicate<Object> valuePredicate = null;
			if (FieldType.STRING.equals(dynamicField.getType())) {
				valuePredicate = value -> (value instanceof Attribute ? ((Attribute) value).getValue() : value) instanceof String;
			}
			else if (FieldType.NUMBER.equals(dynamicField.getType())) {
				valuePredicate = value -> Util.tryToParseAsNumber(value instanceof Attribute ? ((Attribute) value).getValue() : value).isPresent();
			}
			else if (dynamicField.getType() != null) {
				log.warn("dynamic field configuration with type={} not supported. Will not use type as match criterion", dynamicField.getType());
				valuePredicate = Predicates.alwaysTrue();
			}

			// hack around: criterion to make sure a dynamic field should only
			// be used for attributes
			// XXX find better solution to make dynamic fields only work for
			// attributes
			if ("attribute".equals(dynamicField.getName())) {
				if (FieldType.STRING.equals(dynamicField.getType())) {
					valuePredicate = valuePredicate.and(value -> value instanceof Attribute);
				}
				else if (FieldType.NUMBER.equals(dynamicField.getType())) {
					valuePredicate = valuePredicate.and(value -> value instanceof Attribute);
				}
			}

			Collection<String> sourceNames = dynamicField.getSourceNames();
			if (dynamicField.getSourceNames().isEmpty()) {
				sourceNames = Collections.singletonList(".*");
			}

			for (final String sourceName : sourceNames) {
				dynamicFields.add(
						new DynamicFieldConfig(
								Pattern.compile(sourceName).asPredicate(),
								valuePredicate,
								dynamicField));
			}
		}
	}

	private void updateFieldIndexes(Field f) {
		for (FieldUsage usage : f.getUsage()) {
			fieldsByUsage.computeIfAbsent(usage, x -> new HashMap<>())
					.put(f.getName(), f);
		}
		fieldsByType.computeIfAbsent(f.getType(), x -> new HashMap<>())
				.put(f.getName(), f);
	}

	public Map<String, Field> getFieldsByUsage(FieldUsage usage) {
		Map<String, Field> fields = fieldsByUsage.get(usage);
		if (fields == null) {
			fields = Collections.emptyMap();
		}
		return fields;
	}

	public Map<String, Field> getFieldsByType(FieldType type) {
		Map<String, Field> fields = fieldsByType.get(type);
		if (fields == null) {
			fields = Collections.emptyMap();
		}
		return fields;
	}

	// TODO: move logic to indexer
	// => since it is only necessary at the indexer to determine into which field we
	// want to put the document.categories, we should move that logic into the
	// indexer.
	private Optional<Field> determineDefaultCategoryField(Map<String, Field> categoryFields) {
		// if there are several fields of type category, try to determine which
		// one is the most suitable for Document::categories
		if (categoryFields.size() > 1) {
			// best case: one of the fields has one of these preferred names:
			for (String preferedCatFieldName : new String[] { "categories", "category" }) {
				if (categoryFields.containsKey(preferedCatFieldName)) {
					categoryFields.entrySet().removeIf(e -> !e.getKey().equals(preferedCatFieldName));
					log.info("Multiple category fields defined! Will index Document::categories into field with prefered name '{}'!", preferedCatFieldName);
					break;
				}
			}

			// alternative case: if one field has no "source field names",
			// it should be the categories field.
			if (categoryFields.size() > 1) {
				categoryFields.entrySet().removeIf(e -> e.getValue().getSourceNames().size() > 0);
				if (categoryFields.isEmpty()) {
					log.info("Multiple category fields defined, but none with one of the prefered names (categories/category) or without source names found!"
							+ " Won't index Document::categories data!");
				}
			}

			// last case: we don't know which one is the most suitable one.
			// Don't index categories at all.
			if (categoryFields.size() > 1) {
				categoryFields.clear();
				log.warn("Multiple category fields defined, but none has unique characteristic! Won't index Document::categories data!");
			}
		}
		return categoryFields.isEmpty() ? Optional.empty() : Optional.of(categoryFields.values().iterator().next());
	}

	/**
	 * Get field by unique field name.
	 * 
	 * @param fieldName
	 * @return
	 */
	public Optional<Field> getField(String fieldName) {
		return fields.containsKey(fieldName) ? Optional.of(fields.get(fieldName)) : Optional.ofNullable(generatedFields.get(fieldName));
	}

	/**
	 * Get all fields that have the the specified name as field-name or
	 * source-field. If source-fields are not given at initialization (such at
	 * the search service), this function works similar to
	 * {@code getField(String)}
	 * 
	 * @param fieldName
	 * @return
	 */
	public Set<Field> getMatchingFields(String fieldName) {
		IdentityHashMap<Field, Void> matchingFields = new IdentityHashMap<>();

		getField(fieldName).ifPresent(f -> matchingFields.put(f, null));

		List<Field> bySource = fieldsBySource.get(fieldName);
		if (bySource != null && !bySource.isEmpty()) {
			bySource.forEach(f -> matchingFields.put(f, null));
		}

		return matchingFields.keySet();
	}

	/**
	 * Get field with that name and the specified usage.
	 * 
	 * @param fieldName
	 * @param usage
	 * @return
	 */
	public Optional<Field> getMatchingField(String fieldName, FieldUsage usage) {
		return Optional.ofNullable(getFieldsByUsage(usage).get(fieldName));
	}

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
	public Set<Field> getMatchingFields(String fieldName, Object value) {
		Set<Field> matchingFields = getMatchingFields(fieldName);
		if (!matchingFields.isEmpty()) {
			return matchingFields;
		}

		// return first matching dynamic field
		Field generatedField = null;
		if (dynamicFields != null) {
			for (DynamicFieldConfig dynamicFieldConf : dynamicFields) {
				if (dynamicFieldConf.matches(fieldName, value)) {
					generatedField = cloneField(dynamicFieldConf.fieldConfig);
					generatedField.setName(fieldName);
					generatedFields.put(fieldName, generatedField);
					updateFieldIndexes(generatedField);
					break;
				}
			}
		}

		return generatedField == null ? Collections.emptySet() : Collections.singleton(generatedField);
	}

	/**
	 * /**
	 * Similar to {@code getMatchingField(String, Object)} but additionally
	 * tries to generate a field configuration based on the dynamic fields.
	 * Those fields are then filtered by the specified {@link FieldUsage}.
	 * 
	 * @param fieldName
	 * @param value
	 * @param usage
	 * @return
	 */
	public Optional<Field> getMatchingField(String fieldName, Object value, FieldUsage usage) {
		return getMatchingFields(fieldName, value)
				.stream()
				.filter(f -> f.getUsage().contains(FieldUsage.FACET))
				.findFirst();
	}

	private static Field cloneField(final Field original) {
		final Field clone = new Field();
		for (Method m : Field.class.getMethods()) {
			if (m.getName().startsWith("set") && m.getParameterCount() == 1) {
				try {
					Method getter = Field.class.getMethod(m.getName().replaceFirst("set", "get"));
					Object property = getter.invoke(original);
					m.invoke(clone, property);
				}
				catch (Exception e) {
					throw new IllegalStateException(
							"problem mapping a getter method to the setter method '" + m.getName() + "'", e);
				}
			}
		}
		return clone;
	}

}
