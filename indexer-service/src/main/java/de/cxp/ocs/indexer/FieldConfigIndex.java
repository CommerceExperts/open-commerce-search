package de.cxp.ocs.indexer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfiguration;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.util.Util;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FieldConfigIndex {

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

	@NonNull
	private final Map<String, Field> fields = new HashMap<>();

	@Getter
	private final Optional<Field> categoryField;

	private final List<DynamicFieldConfig> dynamicFields = new ArrayList<>();

	/**
	 * Constructor of the converter that prepares the given field configurations
	 * for converting Documents into {@link IndexableItem}.
	 * 
	 * @param standardFields
	 * @param dynamicFields
	 */
	public FieldConfigIndex(FieldConfiguration fieldConfiguration) {

		// create name based index of standard fields
		Map<String, Field> categoryFields = new HashMap<>();
		for (Field field : fieldConfiguration.getFields().values()) {
			fields.put(field.getName(), field);

			if (FieldType.category.equals(field.getType())) {
				categoryFields.put(field.getName(), field);
			}

			for (String sourceName : field.getSourceNames()) {
				Field conflictingField = fields.put(sourceName, field);
				if (conflictingField != null && conflictingField != field) {
					log.warn("double usage of sourceName {} at fields {} and {}", sourceName, field.getName(),
							conflictingField.getName());
				}
			}
		}

		// create index for dynamic fields. each key is a predicate that is
		// tested with the according field name and its value
		for (Field dynamicField : fieldConfiguration.getDynamicFields()) {
			for (final String sourceName : dynamicField.getSourceNames()) {
				dynamicFields.add(
						new DynamicFieldConfig(
								Pattern.compile(sourceName).asPredicate(),
								null,
								dynamicField));
			}
			if (dynamicField.getSourceNames().isEmpty()) {
				if (FieldType.string.equals(dynamicField.getType())) {
					dynamicFields.add(new DynamicFieldConfig(null, value -> value instanceof String, dynamicField));
				}
				else if (FieldType.number.equals(dynamicField.getType())) {
					dynamicFields.add(new DynamicFieldConfig(null, value -> Util.tryToParseAsNumber(value).isPresent(), dynamicField));
				}
				else {
					log.warn("dynamic field configuration without sourceNames and fieldUsage={} are not handled", dynamicField.getType());
				}
			}
		}

		categoryField = determineDefaultCategoryField(categoryFields);
	}

	private Optional<Field> determineDefaultCategoryField(Map<String, Field> categoryFields) {
		// if there are several fields of type category, try to determine which
		// one is the most suitable for Document::categories
		if (categoryFields.size() > 1) {
			// best case: one of the fields has one of these preferred names:
			for (String preferedCatFieldName : new String[] { "categories", "category" }) {
				if (categoryFields.containsKey(preferedCatFieldName)) {
					categoryFields.entrySet().removeIf(e -> !e.getKey().equals(preferedCatFieldName));
					log.warn("Multiple category fields defined! Will index Document::categories into field with prefered name '{}'!", preferedCatFieldName);
					break;
				}
			}

			// alternative case: if one field has no "source field names",
			// it should be the categories field.
			if (categoryFields.size() > 1) {
				categoryFields.entrySet().removeIf(e -> e.getValue().getSourceNames().size() > 0);
				if (categoryFields.isEmpty()) {
					log.warn("Multiple category fields defined, but none with one of the prefered names (categories/category) or without source names found!"
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

	public Optional<Field> getMatchingField(String fieldName, Object value) {
		Field field = fields.get(fieldName);
		// exact matching field name
		if (field != null) return Optional.of(field);

		// return first matching dynamic field
		for (DynamicFieldConfig dynamicFieldConf : dynamicFields) {
			if (dynamicFieldConf.matches(fieldName, value)) {
				return Optional.of(dynamicFieldConf.fieldConfig);
			}
		}

		// no result
		return Optional.empty();
	}

}
