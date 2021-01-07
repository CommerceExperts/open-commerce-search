package de.cxp.ocs.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Util {

	private Util() {};

	private static Pattern numPattern = Pattern.compile("-?\\d+(\\.\\d+)?(E-\\d+)?|(-?\\.\\d+)");

	public static Optional<Number> tryToParseAsNumber(final Object numVal) {
		if (numVal == null) return Optional.empty();
		if (numVal instanceof Number) return Optional.of((Number) numVal);

		String value = numVal.toString().trim();
		final Matcher numMatcher = numPattern.matcher(value);
		if (numMatcher.matches()) {
			if (numMatcher.group(1) == null && numMatcher.group(2) == null && numMatcher.group(3) == null) {
				try {
					if (value.length() < 10) {
						return Optional.of(Integer.parseInt(value));
					}
					else {
						return Optional.of(Long.parseLong(value));
					}
				}
				catch (NumberFormatException nfe2) {
					return Optional.empty();
				}
			}
			else {
				return Optional.of(Float.parseFloat(value));
			}
		}
		return Optional.empty();
	}

	/**
	 * If both values are not null, this method creates a flat collection of
	 * them. By default a HashSet is used as collection. If this is not wanted,
	 * pass another collection type as value.
	 * 
	 * If one value is null, the other is returned and no collection is created.
	 * 
	 * The inner types of the collection are not checked, so it's possible to
	 * merge different types into a collection.
	 * 
	 * If a given value is an array, it will be transformed into a collection.
	 * Multidimensional arrays become a collection of array.
	 * 
	 * @param oldValue
	 * @param newValue
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static Object collectObjects(Object oldValue, Object newValue) {
		if (oldValue == null) return newValue;
		if (newValue == null) return oldValue;
		if (oldValue.equals(newValue)) return oldValue;

		if (oldValue instanceof Collection<?>) {
			if (newValue instanceof Collection<?>) {
				((Collection<Object>) oldValue).addAll((Collection<Object>) newValue);
			}
			else if (newValue.getClass().isArray()) {
				((Collection<Object>) oldValue).addAll(Arrays.asList((Object[]) newValue));
			}
			else {
				((Collection<Object>) oldValue).add(newValue);
			}
			return oldValue;
		}
		else if (newValue instanceof Collection<?>) {
			if (oldValue instanceof Collection<?>) {
				((Collection<Object>) newValue).addAll((Collection<Object>) oldValue);
			}
			else if (newValue.getClass().isArray()) {
				((Collection<Object>) newValue).addAll(Arrays.asList((Object[]) oldValue));
			}
			else {
				((Collection<Object>) newValue).add(oldValue);
			}
			return newValue;
		}
		else {
			Set<Object> collection = new HashSet<>();
			if (oldValue.getClass().isArray()) {
				collection.addAll(Arrays.asList((Object[]) oldValue));
			}
			else {
				collection.add(oldValue);
			}
			if (newValue.getClass().isArray()) {
				collection.addAll(Arrays.asList((Object[]) newValue));
			}
			else {
				collection.add(newValue);
			}
			return collection;
		}
	}

	public static Collection<String> toStringCollection(Object value) {
		return toConvertedCollection(String.class, value, String::valueOf);
	}

	private static Number parseAsNumberOrNull(Object value) {
		if (value instanceof Number) return (Number) value;
		if (value instanceof String) return tryToParseAsNumber((String) value).orElse(null);
		return null;
	}

	public static Collection<Number> toNumberCollection(Object value) {
		return toConvertedCollection(Number.class, value, Util::parseAsNumberOrNull);
	}

	private static <T> Collection<T> toConvertedCollection(Class<T> targetType, Object value,
			Function<Object, T> convert) {
		if (value == null) return Collections.emptyList();
		if (targetType.isInstance(value)) {
			return Arrays.asList(targetType.cast(value));
		}
		else if (value instanceof Collection) {
			List<T> newCollection = new ArrayList<>();
			((Collection<?>) value).forEach(val -> {
				T converted = convert.apply(val);
				if (converted != null) newCollection.add(convert.apply(val));
			});
			return newCollection;
		}
		else if (value.getClass().isArray()) {
			List<T> newCollection = new ArrayList<>();
			for (Object val : (Object[]) value) {
				T converted = convert.apply(val);
				if (converted == null) continue;
				newCollection.add(converted);
			}
			return newCollection;
		}
		T converted = convert.apply(value);
		return converted == null ? Collections.emptyList() : Arrays.asList(converted);
	}

	public static Map<String, String> asMap(String... keyValues) {
		Map<String, String> result = new HashMap<>();
		for (int i = 0; i + 1 < keyValues.length; i++) {
			result.put(keyValues[i], keyValues[i + 1]);
		}
		return result;
	}

	/**
	 * Checks weather the passed value is a {@link String} {@link Collection} or
	 * not.
	 * 
	 * @param value
	 *        the value to check.
	 * @return <code>true</code> if the passed object is an string collection,
	 *         <code>false</code> otherwise.
	 */
	public static boolean isStringCollection(Object value) {
		if (value != null && value instanceof Collection<?>) {
			Collection<?> valueCollection = (Collection<?>) value;
			return !valueCollection.isEmpty() && valueCollection.iterator().next() instanceof String;
		}
		return false;
	}

	public static List<String> deduplicateAdjoinedTokens(String[] inputTokens) {
		List<String> output = new ArrayList<>();
		String lastToken = null;
		for (String token : inputTokens) {
			if (!token.equals(lastToken)) {
				lastToken = token;
				output.add(token);
			}
		}
		return output;
	}
}
