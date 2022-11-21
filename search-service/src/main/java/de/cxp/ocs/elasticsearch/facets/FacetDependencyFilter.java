package de.cxp.ocs.elasticsearch.facets;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.PathResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.util.SearchQueryBuilder;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FacetDependencyFilter implements FacetFilter {

	private final static char PARAM_DELIMITER = '&';
	private final static char PARAM_NAME_VALUE_DELIMITER = '=';
	private final static String FILTER_VALUE_WILDCARD = "*";

	private final Map<String, FacetConfig> facetsBySourceField;

	private final Map<String, FacetDisplayCondition> facetDisplayConditionIndex;

	public FacetDependencyFilter(Map<String, FacetConfig> facetsBySourceField) {
		this.facetsBySourceField = new HashMap<>(facetsBySourceField);
		facetDisplayConditionIndex = new HashMap<>();

		// check the facet configs for their filter dependencies and index them for
		// faster validation
		facetsBySourceField.forEach((sourceField, config) -> {
			if (config.getFilterDependencies() != null && config.getFilterDependencies().length > 0) {
				buildFacetDisplayCondition(config.getFilterDependencies()).ifPresent(
						facetDisplayCondition -> facetDisplayConditionIndex.put(sourceField, facetDisplayCondition));
			}
		});
	}

	@Override
	public boolean isVisibleFacet(Facet facet, FacetConfig config, FilterContext filterContext, int totalMatchCount) {
		FacetDisplayCondition facetDisplayCondition = facetDisplayConditionIndex.get(config.getSourceField());

		return facetDisplayCondition == null
				|| facetDisplayCondition.isVisibleFacet(filterContext.getInternalFilters());
	}

	@AllArgsConstructor
	private class Filter {
		String name;
		Set<String> values;
	}

	private class FiltersMatchCondition {
		final Filter[] allMustMatch;

		public boolean test(Map<String, InternalResultFilter> filtersByName) {
			int expectedFilterMatches = allMustMatch.length;
			for (Filter filterCondition : allMustMatch) {
				InternalResultFilter internalResultFilter = filtersByName.get(filterCondition.name);
				if (internalResultFilter == null) {
					// filter does not exist
					return false;
				}
				// condition just needs any filter-value
				if (filterCondition.values.contains(FILTER_VALUE_WILDCARD)) {
					expectedFilterMatches--;
					continue;
				}

				String[] filterValues = internalResultFilter.getValues();
				int expectedValuesMatches = filterCondition.values.size();
				for (String activeFilter : filterValues) {
					if (filterCondition.values.contains(activeFilter)) {
						expectedValuesMatches--;
					}
					// if we match to a path, a prefix path also has to match
					else if (internalResultFilter instanceof PathResultFilter
							&& filterCondition.values.stream().anyMatch(val -> activeFilter.startsWith(val)
									&& activeFilter.charAt(val.length()) == PathResultFilter.PATH_SEPARATOR)) {
						expectedValuesMatches--;
					}
					if (expectedValuesMatches == 0) {
						break;
					}
				}
				if (expectedValuesMatches == 0) {
					expectedFilterMatches--;
				} else {
					// filter was found, but it contains not all necessary values
					return false;
				}
			}
			return expectedFilterMatches == 0;
		}

		public FiltersMatchCondition(Map<String, Set<String>> parsedFilters) {
			allMustMatch = parsedFilters.entrySet().stream()
					.map(entry -> new Filter(entry.getKey(), entry.getValue()))
					.toArray(Filter[]::new);
		}
	}
	
	@RequiredArgsConstructor
	private class FacetDisplayCondition {
		final Set<String> commonRequiredFilters;
		final List<FiltersMatchCondition> anyMustMatch;
		
		public boolean isVisibleFacet(Map<String, InternalResultFilter> filtersByName) {
			// fast exit: no filters are set at all
			if (filtersByName.isEmpty())
				return false;

			// fast exit: common required filters are not available
			if (!commonRequiredFilters.stream().allMatch(filtersByName::containsKey))
				return false;

			// the costly part: check if any rule matches
			return anyMustMatch.stream().anyMatch(condition -> condition.test(filtersByName));
		}
	}

	private Optional<FacetDisplayCondition> buildFacetDisplayCondition(String[] filterDependencies) {
		final Map<String, AtomicInteger> filterConditionCounts = new HashMap<>(filterDependencies.length);
		List<FiltersMatchCondition> anyMustMatch = new ArrayList<>();
		for (String filterDependency : filterDependencies) {
			Map<String, Set<String>> parsedFilters = parse(filterDependency);

			// count filter-names to see if one filter is required all the time and use that
			// as a fast precondition
			parsedFilters.keySet().forEach(filterName -> filterConditionCounts
					.computeIfAbsent(filterName, f -> new AtomicInteger()).incrementAndGet());

			if (!parsedFilters.isEmpty()) {
				anyMustMatch.add(new FiltersMatchCondition(parsedFilters));
			}
		}

		if (!anyMustMatch.isEmpty()) {
			Set<String> commonRequiredFilters = filterConditionCounts.entrySet().stream()
					// each filter that appeared at every filter is a common required filter
					.filter(entry -> entry.getValue().get() == filterDependencies.length).map(Entry::getKey)
					.collect(Collectors.toSet());

			return Optional.of(new FacetDisplayCondition(commonRequiredFilters, anyMustMatch));
		} else {
			return Optional.empty();
		}
	}

	private Map<String, Set<String>> parse(String filterDependency) {
		String[] singleFilters = StringUtils.split(filterDependency, PARAM_DELIMITER);
		Map<String, Set<String>> uniqueFilters = new HashMap<>();

		for (String rawFilter : singleFilters) {
			String[] filterPair = StringUtils.split(rawFilter, PARAM_NAME_VALUE_DELIMITER);
			if (filterPair.length != 2 || filterPair[0].isBlank() || filterPair[1].isBlank()) {
				log.warn("dropping invalid filter dependency {}", rawFilter);
				continue;
			}
			String[] filterValueSplit = StringUtils.split(filterPair[1], SearchQueryBuilder.VALUE_DELIMITER);
			Set<String> filterValues = new HashSet<String>(filterValueSplit.length);
			for (String filterValueRaw : filterValueSplit) {
				filterValues.add(decode(filterValueRaw).toLowerCase(Locale.ROOT));
			}
			uniqueFilters.put(decode(filterPair[0]), filterValues);
		}
		return uniqueFilters;
	}

	private String decode(String filterValueRaw) {
		try {
			return URLDecoder.decode(filterValueRaw, StandardCharsets.UTF_8);
		} catch (Exception e) {
			log.warn("malformed url parameter defined as filter-dependency: {}", filterValueRaw);
		}
		return filterValueRaw;
	}
}
