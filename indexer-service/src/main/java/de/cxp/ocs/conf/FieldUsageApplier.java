package de.cxp.ocs.conf;

import static de.cxp.ocs.util.Util.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.indexer.model.DataItem;
import de.cxp.ocs.indexer.model.FacetEntry;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.indexer.model.MasterItem;
import de.cxp.ocs.indexer.model.VariantItem;
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
		if (value == null
				|| (record instanceof MasterItem && !field.isMasterLevel())
				|| (record instanceof VariantItem && !field.isVariantLevel())
				|| (value instanceof String && ((String) value).isEmpty())
				|| (value instanceof Collection<?> && ((Collection<?>) value).isEmpty())
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
		}
	}

	public static void handleSearchField(final DataItem record, final Field field, Object value) {
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
			if (value instanceof String) {
				value = field.getSearchContentPrefix() + " " + value;
			}
			else {
				value = field.getSearchContentPrefix() + " " + value.toString();
			}
		}

		record.getSearchData().compute(fieldName, joinDataValueFunction(value));
		if (record instanceof VariantItem) {
			((VariantItem) record).getMaster().getSearchData().compute(fieldName, joinDataValueFunction(value));
		}
	};

	public static void handleResultField(final DataItem record, final Field field, Object value) {
		if (FieldType.CATEGORY.equals(field.getType())) {
			value = convertCategoryDataToString(value, FieldUsageApplier::toCategoryPathString);
		}

		String fieldName = field.getName();
		record.getResultData().compute(fieldName, joinDataValueFunction(value));
	};

	public static void handleSortField(final DataItem record, final Field field, Object value) {
		if (isEmpty(value)) {
			return;
		}

		String fieldName = field.getName();
		if (value instanceof Attribute) {
			value = ((Attribute) value).getValue();
		}

		value = ensureCorrectValueType(field, value);

		Object previousValue = record.getSortData().putIfAbsent(fieldName, new MinMaxSet<>(value));
		if (previousValue != null) {
			record.getSortData().compute(fieldName, joinDataValueFunction(value));
		}

		if (record instanceof VariantItem) {
			previousValue = ((VariantItem) record).getMaster().getSortData()
					.putIfAbsent(fieldName, new MinMaxSet<>(value));
			if (previousValue != null) {
				((VariantItem) record).getMaster().getSortData().compute(fieldName, joinDataValueFunction(
						value));
			}
		}
	}

	private static boolean isEmpty(Object value) {
		return value == null
				|| value instanceof String && ((String) value).isEmpty()
				|| value instanceof Collection && ((Collection<?>) value).isEmpty()
				|| value.getClass().isArray() && ((Object[]) value).length == 0;
	}

	private static Object ensureCorrectValueType(final Field field, final Object value) {
		Object parsedValue = value;
		if (FieldType.NUMBER.equals(field.getType()) && !(value instanceof Number)) {
			if (value instanceof Collection || value.getClass().isArray()) {
				parsedValue = toNumberCollection(value);
			}
			else {
				parsedValue = tryToParseAsNumber(value.toString()).orElseThrow(
						() -> new IllegalArgumentException("value for numeric field " + field.getName()
								+ " is not numeric: " + value.toString().substring(0, 12)
								+ (value.toString().length() > 12 ? "..." : "")));
			}
		}
		if (FieldType.CATEGORY.equals(field.getType())) {
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
	 * <p>
	 * For fields that should be indexed on both levels, facets will only be
	 * indexed on variant level to avoid conflicts during facet creation.
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
		if (isEmpty(value) || field.isBothLevel() && record instanceof MasterItem) {
			return;
		}

		switch (field.getType()) {
			case NUMBER:
				handleNumberFacetData(record, field, value);
				break;
			case CATEGORY:
				handleCategoryFacetData(record, field, value);
				break;
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
							new FacetEntry<>(field.getName(), null, numVal)));
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

		if (value instanceof Number) {
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
		else {
			// allow non-numeric values - they should be processed by date
			// detection
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
