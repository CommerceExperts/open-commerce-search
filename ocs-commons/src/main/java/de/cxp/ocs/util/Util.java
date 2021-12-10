package de.cxp.ocs.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
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
			try {
				unwrapIterable(newValue, ((Collection<Object>) oldValue)::addAll, ((Collection<Object>) oldValue)::add);
				return oldValue;
			}
			catch (UnsupportedOperationException gnah) {
				// oldValue seems to be immutable, try to put into "newValue"...
			}
		}

		if (newValue instanceof Collection<?>) {
			try {
				unwrapIterable(oldValue, ((Collection<Object>) newValue)::addAll, ((Collection<Object>) newValue)::add);
				return newValue;
			}
			catch (UnsupportedOperationException gnah) {
				// newValue seems to be immutable,
				// try to put the values into a new collection
			}
		}

		Set<Object> collection = new LinkedHashSet<>();
		unwrapIterable(oldValue, collection::addAll, collection::add);
		unwrapIterable(newValue, collection::addAll, collection::add);
		return collection;
	}

	@SuppressWarnings("unchecked")
	private static void unwrapIterable(Object o, Consumer<Collection<Object>> collectionConsumer, Consumer<Object> objectConsumer) {
		if (o == null) return;
		if (o instanceof Collection<?>) {
			collectionConsumer.accept(((Collection<Object>) o));
		}
		else if (o.getClass().isArray()) {
			collectionConsumer.accept(Arrays.asList((Object[]) o));
		}
		else {
			objectConsumer.accept(o);
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

	public static Object ensureSameType(Object referenceObj, Object adjustableObj) {
		if (referenceObj.getClass().equals(adjustableObj.getClass()) || referenceObj.getClass().isAssignableFrom(adjustableObj.getClass())) {
			return adjustableObj;
		}
		else if (referenceObj instanceof Number && adjustableObj instanceof Number) {
			// ordered by likeliness
			if (referenceObj instanceof Float) {
				return ((Number) adjustableObj).floatValue();
			}
			if (referenceObj instanceof Double) {
				return ((Number) adjustableObj).doubleValue();
			}
			if (referenceObj instanceof Long) {
				return ((Number) adjustableObj).longValue();
			}
			if (referenceObj instanceof Integer) {
				return ((Number) adjustableObj).intValue();
			}
			if (referenceObj instanceof Byte) {
				return ((Number) adjustableObj).byteValue();
			}
			if (referenceObj instanceof Short) {
				return ((Number) adjustableObj).shortValue();
			}
		}
		if (referenceObj instanceof String) {
			return adjustableObj.toString();
		}
		throw new IllegalArgumentException("Can't convert object of type '" + adjustableObj.getClass() + "' to type '" + referenceObj.getClass() + "'");
	}

	/**
	 * Make sure, that if we have a number here, it will be represented by
	 * float.
	 * If it's not a number, the object will be returned as is.
	 * 
	 * @return
	 */
	public static Object ensureNumberIsFloat(Object x) {
		if (x instanceof Number) return ((Number) x).floatValue();
		if (x instanceof Number[]) {
			float[] copy = new float[((Number[]) x).length];
			for (int i = 0; i < copy.length; i++) {
				copy[i] = ((Number[]) x)[i].floatValue();
			}
			return copy;
		}
		else return x;
	}

	public static boolean isEmpty(Object value) {
		return value == null
				|| value instanceof String && ((String) value).isEmpty()
				|| value instanceof Collection && ((Collection<?>) value).isEmpty()
				|| value.getClass().isArray() && ((Object[]) value).length == 0;
	}
}
