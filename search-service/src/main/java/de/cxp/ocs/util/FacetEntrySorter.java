package de.cxp.ocs.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig.ValueOrder;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.HierarchialFacetEntry;

public class FacetEntrySorter {

	public final static FacetEntrySorter NOOP = new FacetEntrySorter(null, 0);

	private final static EnumMap<ValueOrder, FacetEntrySorter> DEFAULT_INSTANCES = new EnumMap<>(ValueOrder.class);
	static {
		// Some sorting variants are basically state-less and can be reused across facets
		DEFAULT_INSTANCES.put(ValueOrder.ALPHANUM_ASC, new FacetEntrySorter(ValueOrder.ALPHANUM_ASC, 0));
		DEFAULT_INSTANCES.put(ValueOrder.ALPHANUM_DESC, new FacetEntrySorter(ValueOrder.ALPHANUM_DESC, 0));
		DEFAULT_INSTANCES.put(ValueOrder.COUNT, new FacetEntrySorter(ValueOrder.COUNT, 0));
	}

	public static FacetEntrySorter of(ValueOrder valueOrder, int estimatedFacetValues) {
		if (valueOrder == null) return NOOP;
		return Optional.ofNullable(DEFAULT_INSTANCES.get(valueOrder))
				.orElseGet(() -> new FacetEntrySorter(valueOrder, estimatedFacetValues));
	}

	private final Integer						defaultOrderValue;
	private final LoadingCache<String, Integer>	numericTextParseCache;
	private final Comparator<FacetEntry>		valueComparator;

	private final Pattern numberPattern = Pattern.compile("\\p{N}+");

	private FacetEntrySorter(ValueOrder valueOrder, int estimatedValueCardinality) {
		if (ValueOrder.HUMAN_NUMERIC_ASC.equals(valueOrder) || ValueOrder.HUMAN_NUMERIC_DESC.equals(valueOrder)) {
			defaultOrderValue = ValueOrder.HUMAN_NUMERIC_ASC.equals(valueOrder) ? Integer.MAX_VALUE : Integer.MIN_VALUE;
			numericTextParseCache = initParserCache(estimatedValueCardinality);
		}
		else {
			defaultOrderValue = 0;
			numericTextParseCache = null;
		}

		valueComparator = valueOrder != null ? initComparators(valueOrder) : null;
	}

	private Comparator<FacetEntry> initComparators(ValueOrder valueOrder) {
		switch (valueOrder) {
			case ALPHANUM_ASC:
				return Comparator.comparing(FacetEntry::getKey);
			case ALPHANUM_DESC:
				return Comparator.comparing(FacetEntry::getKey).reversed();
			case HUMAN_NUMERIC_ASC:
				return Comparator.comparing(this::parseNumber).thenComparing(FacetEntry::getKey);
			case HUMAN_NUMERIC_DESC:
				return Comparator.comparing(this::parseNumber).thenComparing(FacetEntry::getKey).reversed();
			case COUNT:
				return Comparator.comparing(FacetEntry::getDocCount).reversed();
			default:
				return null;
		}
	}

	private LoadingCache<String, Integer> initParserCache(int estimatedValueCardinality) {
		return CacheBuilder.newBuilder().maximumSize(estimatedValueCardinality)
				.build(new CacheLoader<String, Integer>() {

					@Override
					public Integer load(String value) throws Exception {
						if (value == null || value.isBlank()) {
							return defaultOrderValue;
						}
						Matcher numberMatcher = numberPattern.matcher(value);
						if (numberMatcher.find()) {
							String match = numberMatcher.group();
							return Util.tryToParseAsNumber(match).map(Number::intValue).orElse(defaultOrderValue);
						}
						else {
							return defaultOrderValue;
						}
					}
				});
	}

	private Integer parseNumber(FacetEntry entry) {
		try {
			return numericTextParseCache.get(entry.getKey());
		}
		catch (ExecutionException e) {
			return defaultOrderValue;
		}
	}

	public void sort(Facet facet) {
		if (valueComparator != null) {
			Collections.sort(facet.entries, valueComparator);
			facet.entries.forEach(parent -> sortChildren(parent, valueComparator));
		}
	}

	private void sortChildren(FacetEntry parent, Comparator<FacetEntry> valueComparator) {
		if (parent instanceof HierarchialFacetEntry && ((HierarchialFacetEntry) parent).getChildren().size() > 1) {
			Collections.sort(((HierarchialFacetEntry) parent).getChildren(), valueComparator);
			((HierarchialFacetEntry) parent).getChildren().forEach(subParent -> sortChildren(subParent, valueComparator));
		}
	}

}
