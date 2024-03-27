package de.cxp.ocs.conf;

import static de.cxp.ocs.util.Util.collectObjects;
import static de.cxp.ocs.util.Util.ensureSameType;
import static de.cxp.ocs.util.Util.isEmpty;
import static de.cxp.ocs.util.Util.toNumberCollection;
import static de.cxp.ocs.util.Util.toStringCollection;
import static de.cxp.ocs.util.Util.tryToParseAsNumber;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.indexer.model.*;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.util.MinMaxSet;
import io.micrometer.core.lang.NonNull;

/**
 * Enum describing the usage of an field that will be indexed.
 */
@SuppressWarnings("unchecked")
public class FieldUsageApplier {

	public static void applyAll(final DataItem record, final Field field, final Object value) {
		if (isEmpty(value)
				|| (record instanceof MasterItem && !field.isMasterLevel())
				|| (record instanceof VariantItem && !field.isVariantLevel())
				|| field.getUsage() == null
				|| field.getUsage().isEmpty()) {
			return;
		}

		for (final FieldUsage fu : field.getUsage()) {
			apply(fu, record, field, value);
		}
	}

	public static void apply(FieldUsage fieldUsage, DataItem indexableItem, Field field, @NonNull Object value) {
		switch (fieldUsage) {
			case FACET:
				handleFacetField(indexableItem, field, value);
				break;
			case RESULT:
				handleResultField(indexableItem, field, value);
				break;
			case SORT:
				handleSortField(indexableItem, field, value);
				break;
			case SCORE:
				handleScoreField(indexableItem, field, value);
				break;
			case SEARCH:
				handleSearchField(indexableItem, field, value);
				break;
			case FILTER:
				if (!field.hasUsage(FieldUsage.FACET)) {
					handleFilterField(indexableItem, field, value);
				}
				break;
			default:
				break;
		}
	}

	private static void handleSearchField(final DataItem record, final Field field, Object value) {
		if (isEmpty(value)) {
			return;
		}

		String fieldName = field.getName();
		if (value instanceof Attribute) {
			value = ((Attribute) value).getValue();
		}

		if (FieldType.CATEGORY.equals(field.getType())) {
			// for search only extract category names
			value = convertCategoryDataToString(value, catPath -> {
				StringBuilder searchableCategoryPath = new StringBuilder();
				for (Category c : catPath) {
					if (searchableCategoryPath.length() > 0) searchableCategoryPath.append(" / ");
					searchableCategoryPath.append(c.getName());
				}
				return searchableCategoryPath.toString();
			});
		}

		if (value instanceof Collection<?>) {
			StringBuilder newValue = new StringBuilder();
			for (Object val : (Collection<?>) value) {
				newValue.append(" ").append(val.toString());
			}
			value = newValue.toString().trim();
		}
		else if (value.getClass().isArray()) {
			StringBuilder newValue = new StringBuilder();
			for (Object val : (Object[]) value) {
				newValue.append(" ").append(val.toString());
			}
			value = newValue.toString().trim();
		}

		if (field.getSearchContentPrefix() != null) {
			value = field.getSearchContentPrefix() + " " + value;
		}

		// in case a raw field is a map, we won't join it as it can not be joined
		if (FieldType.RAW.equals(field.getType()) && value instanceof Map) {
			record.getSearchData().put(fieldName, value);
		}
		else {
			record.getSearchData().compute(fieldName, joinDataValueFunction(value));
			if (record instanceof VariantItem) {
				((VariantItem) record).getMaster().getSearchData().compute(fieldName, joinDataValueFunction(value));
			}
		}
	};

	private static void handleResultField(final DataItem record, final Field field, Object value) {
		if (FieldType.CATEGORY.equals(field.getType())) {
			value = convertCategoryDataToString(value, FieldUsageApplier::toCategoryPathString);
		}

		value = ensureCorrectValueType(field, value);

		String fieldName = field.getName();

		// in case a raw field is a map, we won't join it as it can not be joined
		if (FieldType.RAW.equals(field.getType()) && value instanceof Map) {
			record.getResultData().put(fieldName, value);
		}
		else {
			record.getResultData().compute(fieldName, joinDataValueFunction(value));
		}
	};

	private static void handleSortField(final DataItem record, final Field field, Object value) {
		value = unwrapValue(field, value);
		String fieldName = field.getName();
		Object previousValue = record.getSortData().putIfAbsent(fieldName, MinMaxSet.of(value));
		if (previousValue != null && previousValue instanceof MinMaxSet<?>) {
			addValuesToMinMaxSet((MinMaxSet<Object>) previousValue, value);
		}

		if (record instanceof VariantItem) {
			previousValue = ((VariantItem) record).getMaster().getSortData().putIfAbsent(fieldName, MinMaxSet.of(value));
			if (previousValue != null) {
				addValuesToMinMaxSet((MinMaxSet<Object>) previousValue, value);
			}
		}
	}

	private static void handleFilterField(final DataItem record, final Field field, Object value) {
		value = unwrapValue(field, value);
		String fieldName = field.getName();
		Object existingValue = record.getFilterData().putIfAbsent(fieldName, value);
		if (existingValue != null) {
			Object valueCollection = collectObjects(existingValue, value);
			record.getFilterData().put(fieldName, valueCollection);
		}
	}

	private static Object unwrapValue(final Field field, Object value) {
		if (value instanceof Attribute) {
			value = ((Attribute) value).getValue();
		}

		return ensureCorrectValueType(field, value);
	}

	private static void addValuesToMinMaxSet(MinMaxSet<Object> previousValue, Object value) {
		Object referenceValue = ((MinMaxSet<Object>) previousValue).min();
		if (value.getClass().isArray()) {
			for (Object val : (Object[]) value) {
				value = ensureSameType(referenceValue, val);
				((MinMaxSet<Object>) previousValue).add(value);
			}
		} else if (value instanceof Collection<?>) {
			((MinMaxSet<Object>) previousValue).addAll((Collection<Object>) value);
		} else {
			value = ensureSameType(referenceValue, value);
			((MinMaxSet<Object>) previousValue).add(value);
		}
	}

	private static Object ensureCorrectValueType(final Field field, final Object value) {
		Object dataValue = value instanceof Attribute ? ((Attribute) value).value : value;
		Object parsedValue = value;
		if (FieldType.NUMBER.equals(field.getType()) && !(dataValue instanceof Number)) {
			if (dataValue instanceof Collection || dataValue.getClass().isArray()) {
				parsedValue = toNumberCollection(value);
				if (!parsedValue.getClass().isArray()) {
					parsedValue = ((Collection<Number>) parsedValue).toArray(new Number[0]);
				}
			}
			else {
				parsedValue = tryToParseAsNumber(dataValue.toString()).orElseThrow(
						() -> new IllegalArgumentException("value for numeric field " + field.getName()
								+ " is not numeric: "
								+ (dataValue.toString().length() > 15 ? dataValue.toString().substring(0, 12) + "..." : dataValue.toString())));
			}
		}
		else if (FieldType.CATEGORY.equals(field.getType())) {
			parsedValue = convertCategoryDataToString(value, FieldUsageApplier::toCategoryPathString);
		}
		return parsedValue;
	}

	/**
	 * <p>
	 * If the field is set to number type, the value will be indexed as a
	 * numeric facet. Otherwise it will be indexed as String, even if the string
	 * contains a number.
	 * </p>
	 * 
	 * @param record
	 *        where the facet value should be put
	 * @param field
	 *        field configuration of the according field
	 * @param value
	 *        value to be applied to the record
	 */
	public static void handleFacetField(final DataItem record, final Field field, Object value) {
		switch (field.getType()) {
			case NUMBER:
				handleNumberFacetData(record, field, value);
				break;
			case CATEGORY:
				handleCategoryFacetData(record, field, value);
				break;
			case RAW:
				// this should never happen, as it should be cleaned up during configuration validation
				throw new IllegalArgumentException("raw field-type can't be used for facetting! Invalid config for field " + field.getName());
			default:
				handleTermFacetData(record, field, value);
		}

	}

	private static void handleNumberFacetData(final DataItem record, final Field field, Object value) {
		if (value instanceof Collection || value.getClass().isArray()) {
			Collection<Number> numberValues = toNumberCollection(value);
			record.getNumberFacetData().add(new FacetEntry<Number>(field.getName(), numberValues));
		}
		else if (value instanceof Attribute) {
			Attribute attr = ((Attribute) value);
			tryToParseAsNumber(attr.getValue())
					.map(numVal -> record.getNumberFacetData().add(
							new FacetEntry<>(field.getName(), attr.code, numVal)));
		}
		else {
			Optional<Number> numberValue = tryToParseAsNumber(String.valueOf(value));
			numberValue.map(numVal -> record.getNumberFacetData().add(new FacetEntry<>(field.getName(),
					numVal)));
		}
	};

	private static void handleCategoryFacetData(final DataItem record, final Field field, Object value) {
		if (record instanceof IndexableItem) {
			List<FacetEntry<String>> categories = ((IndexableItem) record).getPathFacetData();
			String fieldName = field.getName();

			if (value instanceof Collection) {
				if (((Collection<?>) value).iterator().next() instanceof Category[]) {
					for (Category[] catPath : ((Collection<Category[]>) value)) {
						categories.addAll(toPathFacetEntries(fieldName, catPath));
					}
				}
				else {
					for (Object catPath : ((Collection<?>) value)) {
						categories.addAll(toPathFacetEntry(fieldName, StringUtils.split(catPath.toString(), '/')));
					}
				}
			}
			else if (value instanceof Category[]) {
				categories.addAll(toPathFacetEntries(fieldName, (Category[]) value));
			}
			else if (value instanceof String[]) {
				categories.addAll(toPathFacetEntry(fieldName, (String[]) value));
			}
			else {
				categories.addAll(toPathFacetEntry(fieldName, StringUtils.split(value.toString(), '/')));
			}
		}
	}

	private static void handleTermFacetData(final DataItem record, final Field field, Object value) {
		if (value instanceof Collection || value.getClass().isArray()) {
			Collection<String> stringValues = toStringCollection(value);
			record.getTermFacetData().add(new FacetEntry<String>(field.getName(), stringValues));
		}
		else if (value instanceof Attribute) {
			Attribute attr = (Attribute) value;
			record.getTermFacetData().add(new FacetEntry<>(field.getName(), attr.getCode(), attr.getValue()));
		}
		else {
			record.getTermFacetData().add(new FacetEntry<>(field.getName(), String.valueOf(value)));
		}
	}

	public static void handleScoreField(final DataItem record, final Field field, final Object value) {
		Optional<Number> numValue;
		String fieldName = field.getName();

		if (FieldType.RAW.equals(field.getType())) {
			numValue = Optional.empty();
		}
		else if (value instanceof Number) {
			numValue = Optional.of((Number) value);
		}
		else if (value instanceof List<?>) {
			for (Object v : (List<?>) value) {
				handleScoreField(record, field, v);
			}
			return;
		}
		else if (value instanceof Attribute) {
			fieldName = ((Attribute) value).getName();
			numValue = tryToParseAsNumber(((Attribute) value).getValue());
		}
		else {
			numValue = tryToParseAsNumber(String.valueOf(value));
		}

		if (numValue.isPresent()) {
			record.getScores().compute(fieldName, joinScoreDataValue(numValue.get()));

			if (record instanceof VariantItem) {
				((VariantItem) record).getMaster().getScores()
						.compute(field.getName(), joinScoreDataValue(numValue.get()));
			}
		}
		else if (FieldType.RAW.equals(field.getType()) && value instanceof Map) {
			record.getScores().put(fieldName, value);
		}
		else {
			// allow non-numeric values - they should be processed by date
			// detection or mapping for raw-types
			record.getScores().compute(fieldName, joinDataValueFunction(value));
		}

	};

	protected static BiFunction<? super String, ? super Object, ? extends Object> joinDataValueFunction(
			final Object value) {
		return (name, oldVal) -> collectObjects(oldVal, value);
	}

	protected static BiFunction<? super String, ? super Object, ? extends Object> joinScoreDataValue(
			final Number value) {
		// only keep biggest value
		// XXX are there cases, where a lower value should be kept?
		return (name, oldVal) -> {
			if (oldVal == null) return value;
			// return biggest value
			if (Double.compare(value.doubleValue(), ((Number) oldVal).doubleValue()) < 0) return oldVal;
			if (Double.compare(value.doubleValue(), ((Number) oldVal).doubleValue()) > 0) return value;
			else return oldVal;
		};
	}

	private static Collection<String> convertCategoryDataToString(Object value, Function<Category[], String> toStringMethod) {
		if (value instanceof Category[]) {
			return Collections.singletonList(toStringMethod.apply((Category[]) value));
		}
		else if (value instanceof Collection) {
			Object elem = ((Collection<?>) value).iterator().next();
			Set<String> convertedCategories = new HashSet<String>();
			if (elem instanceof Category[]) {
				for (Category[] path : (Collection<Category[]>) value) {
					convertedCategories.add(toStringMethod.apply((Category[]) path));
				}
			}
			else if (elem instanceof String[]) {
				for (String[] path : (Collection<String[]>) value) {
					convertedCategories.add(StringUtils.join(path, '/'));
				}
			}
			else {
				for (Object path : (Collection<?>) value) {
					convertedCategories.add(path.toString());
				}
			}
			return convertedCategories;
		}
		else if (value instanceof String[]) {
			return Collections.singletonList(StringUtils.join((String[]) value, '/'));
		}
		else {
			// FIXME: this does not make much sense for category facets
			// in case the user wants to filter by category ids only
			return Collections.singletonList(value.toString());
		}
	}

	private static String toCategoryPathString(Category[] categories) {
		StringBuilder categoryPath = new StringBuilder();
		for (Category c : categories) {
			if (categoryPath.length() > 0) categoryPath.append('/');
			categoryPath.append(c.getName().replace("/", "%2F"));
		}
		return categoryPath.toString();
	}

	private static List<FacetEntry<String>> toPathFacetEntries(String fieldName, Category[] catPath) {
		List<FacetEntry<String>> catFacetEntries = new ArrayList<>();
		StringBuilder pathString = new StringBuilder();
		for (Category cat : catPath) {
			if (pathString.length() > 0) pathString.append('/');
			pathString.append(cat.getName().replace("/", "%2F"));
			catFacetEntries.add(new FacetEntry<>(fieldName, pathString.toString()).setId(cat.getId()));
		}
		return catFacetEntries;
	}

	private static List<FacetEntry<String>> toPathFacetEntry(String fieldName, String[] catPath) {
		List<FacetEntry<String>> catFacetEntries = new ArrayList<>();
		StringBuilder pathString = new StringBuilder();
		for (String cat : catPath) {
			if (pathString.length() > 0) pathString.append('/');
			pathString.append(cat.replace("/", "%2F"));

			catFacetEntries.add(new FacetEntry<>(fieldName, pathString.toString()));
		}
		return catFacetEntries;
	}

}
