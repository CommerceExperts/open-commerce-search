package de.cxp.ocs.indexer.model;

import static de.cxp.ocs.util.Util.*;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.util.MinMaxSet;
import lombok.extern.slf4j.Slf4j;

/**
 * Enum describing the usage of an field that will be indexed.
 */
@Slf4j
public class FieldUsageApplier {

	public static void handleSearchField(final DataItem record, final Field field, Object value) {
		if (value == null || value instanceof String && ((String) value).isEmpty()) {
			return;
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

		record.getSearchData().compute(field.getName(), computeSearchDataValue(value));
		if (record instanceof VariantItem) {
			((VariantItem) record).getMaster().getSearchData().compute(field.getName(), computeSearchDataValue(
					value));
		}
	};

	public static void handleResultField(final DataItem record, final Field field, final Object value) {
		record.getResultData().compute(field.getName(), computeSearchDataValue(value));
	};

	public static void handleSortField(final DataItem record, final Field field, Object value) {
		if (value == null || value instanceof String && ((String) value).isEmpty()) {
			return;
		}

		value = ensureCorrectValueType(field, value);

		Object previousValue = record.getSortData().putIfAbsent(field.getName(), new MinMaxSet<>(value));
		if (previousValue != null) {
			record.getSortData().compute(field.getName(), computeSearchDataValue(value));
		}

		if (record instanceof VariantItem) {
			previousValue = ((VariantItem) record).getMaster().getSortData()
					.putIfAbsent(field.getName(), new MinMaxSet<>(value));
			if (previousValue != null) {
				((VariantItem) record).getMaster().getSortData().compute(field.getName(), computeSearchDataValue(
						value));
			}
		}
	};

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
		return parsedValue;
	}

	/**
	 * If value is instance of {@link Number}, it will be indexed as a
	 * numeric facet.
	 * Otherwise it will be indexed as String, even if the string contains a
	 * number.
	 */
	public static void handleFacetField(final DataItem record, final Field field, Object value) {
		if (value == null || value instanceof String && ((String) value).isEmpty() || value instanceof Collection
				&& ((Collection<?>) value).isEmpty() || value.getClass().isArray()
						&& ((Object[]) value).length == 0) {
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
				Collection<String> values = toStringCollection(value);
				((MasterItem) record).getCategories().addAll(values);
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
			for (Object v : (List<?>)value) {
				handleScoreField(record, field, v);
			}
			return;
		}
		else {
			numValue = tryToParseAsNumber(String.valueOf(value));
		}
		if (numValue.isPresent()) {
			record.getScores().compute(field.getName(), computeScoreDataValue(numValue.get()));

			if (record instanceof VariantItem) {
				((VariantItem) record).getMaster().getScores()
						.compute(field.getName(), computeScoreDataValue(numValue.get()));
			}
		}
		else {
			throw new IllegalArgumentException("value must be a numeric value (field: " + field.getName() + ")");
		}

	};

	protected static BiFunction<? super String, ? super Object, ? extends Object> computeSearchDataValue(
			final Object value) {
		return (name, oldVal) -> collectObjects(oldVal, value);
	}

	protected static BiFunction<? super String, ? super Object, ? extends Object> computeScoreDataValue(
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

	public static void apply(FieldUsage fieldUsage, DataItem indexableItem, Field field, Object value) {
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

}
