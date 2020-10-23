package de.cxp.ocs.indexer.model;

import static de.cxp.ocs.util.Util.*;

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
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.util.MinMaxSet;
import io.micrometer.core.lang.NonNull;

/**
 * Enum describing the usage of an field that will be indexed.
 */
public class FieldUsageApplier {

	public static void handleSearchField(final DataItem record, final Field field, Object value) {
		if (isEmpty(value)) {
			return;
		}

		if (FieldType.category.equals(field.getType())) {
			// for search only extract category names
			value = convertCategoryData(value, catPath -> {
				StringBuilder searchableCategoryPath = new StringBuilder();
				for (Category c : catPath) {
					searchableCategoryPath.append(c.getName()).append(" / ");
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

		record.getSearchData().compute(field.getName(), joinSearchDataValue(value));
		if (record instanceof VariantItem) {
			((VariantItem) record).getMaster().getSearchData().compute(field.getName(), joinSearchDataValue(
					value));
		}
	};

	public static void handleResultField(final DataItem record, final Field field, Object value) {
		if (FieldType.category.equals(field.getType())) {
			value = convertCategoryData(value, FieldUsageApplier::toCategoryPathString);
		}
		record.getResultData().compute(field.getName(), joinSearchDataValue(value));
	};

	public static void handleSortField(final DataItem record, final Field field, Object value) {
		if (isEmpty(value)) {
			return;
		}

		value = ensureCorrectValueType(field, value);

		Object previousValue = record.getSortData().putIfAbsent(field.getName(), new MinMaxSet<>(value));
		if (previousValue != null) {
			record.getSortData().compute(field.getName(), joinSearchDataValue(value));
		}

		if (record instanceof VariantItem) {
			previousValue = ((VariantItem) record).getMaster().getSortData()
					.putIfAbsent(field.getName(), new MinMaxSet<>(value));
			if (previousValue != null) {
				((VariantItem) record).getMaster().getSortData().compute(field.getName(), joinSearchDataValue(
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
		if (FieldType.number.equals(field.getType()) && !(value instanceof Number)) {
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
		if (FieldType.category.equals(field.getType())) {
			parsedValue = convertCategoryData(value, FieldUsageApplier::toCategoryPathString);
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
	 */
	public static void handleFacetField(final DataItem record, final Field field, Object value) {
		if (isEmpty(value) || field.isBothLevel() && record instanceof MasterItem) {
			return;
		}

		switch (field.getType()) {
			case number:
				if (value instanceof Collection || value.getClass().isArray()) {
					Collection<Number> numberValues = toNumberCollection(value);
					record.getNumberFacetData().add(new FacetEntry<Number>(field.getName()).withValues(
							numberValues));
				}
				else {
					Optional<Number> numberValue = tryToParseAsNumber(String.valueOf(value));
					numberValue.map(numVal -> record.getNumberFacetData().add(new FacetEntry<>(field.getName(),
							numVal)));
				}
				break;
			case category:
				if (record instanceof IndexableItem) {
					((IndexableItem) record).getCategories()
							.computeIfAbsent(field.getName(), n -> new HashSet<>())
							.addAll(convertCategoryData(value, FieldUsageApplier::toCategoryPathString));
				}
				break;
			default:
				if (value instanceof Collection || value.getClass().isArray()) {
					Collection<String> stringValues = toStringCollection(value);
					record.getTermFacetData().add(new FacetEntry<String>(field.getName()).withValues(stringValues));
				}
				else {
					record.getTermFacetData().add(new FacetEntry<>(field.getName(), String.valueOf(value)));
				}
		}

	};

	public static void handleScoreField(final DataItem record, final Field field, final Object value) {
		final Optional<Number> numValue;
		if (value instanceof Number) {
			numValue = Optional.of((Number) value);
		}
		else if (value instanceof List<?>) {
			for (Object v : (List<?>) value) {
				handleScoreField(record, field, v);
			}
			return;
		}
		else {
			numValue = tryToParseAsNumber(String.valueOf(value));
		}
		if (numValue.isPresent()) {
			record.getScores().compute(field.getName(), joinScoreDataValue(numValue.get()));

			if (record instanceof VariantItem) {
				((VariantItem) record).getMaster().getScores()
						.compute(field.getName(), joinScoreDataValue(numValue.get()));
			}
		}
		else {
			throw new IllegalArgumentException("value must be a numeric value (field: " + field.getName() + ")");
		}

	};

	protected static BiFunction<? super String, ? super Object, ? extends Object> joinSearchDataValue(
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

	public static void apply(FieldUsage fieldUsage, DataItem indexableItem, Field field, @NonNull Object value) {
		switch (fieldUsage) {
			case Facet:
				handleFacetField(indexableItem, field, value);
				break;
			case Result:
				handleResultField(indexableItem, field, value);
				break;
			case Sort:
				handleSortField(indexableItem, field, value);
				break;
			case Score:
				handleScoreField(indexableItem, field, value);
				break;
			case Search:
				handleSearchField(indexableItem, field, value);
				break;
		}
	}

	@SuppressWarnings("unchecked")
	private static Collection<String> convertCategoryData(Object value, Function<Category[], String> toStringMethod) {
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
			return Collections.singletonList(value.toString());
		}
	}

	private static String toCategoryPathString(Category[] categories) {
		StringBuilder categoryPath = new StringBuilder();
		for (Category c : categories) {
			if (categoryPath.length() > 0) categoryPath.append('/');
			categoryPath.append(c.getName().replace("/", "%2F"));
			if (c.getId() != null) categoryPath.append(':').append(c.getId());
		}
		return categoryPath.toString();
	}
}
