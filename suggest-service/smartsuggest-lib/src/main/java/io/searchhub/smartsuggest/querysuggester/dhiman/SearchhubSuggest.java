package io.searchhub.smartsuggest.querysuggester.dhiman;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.search.suggestion.data.Indexable;
import com.search.suggestion.data.Suggestable;

import io.searchhub.smartsuggest.spi.SuggestRecord;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
public class SearchhubSuggest implements Indexable, Suggestable, Serializable {

	private static final long serialVersionUID = -7244514179394050252L;

	private static final Map<String, Integer> noFilters = Collections.emptyMap();

	@Getter
	private final String		bestQuery;

	@Getter
	private final double		weight;

	private final List<String>	variants;

	@Getter
	private final Map<String, Integer> filter;
	
	@Setter
	@Getter
	int count = 1;

	public SearchhubSuggest(SuggestRecord s) {
		bestQuery = s.getPrimaryText();
		weight = s.getWeight();
		variants = Collections.singletonList(s.getSecondaryText());
		filter = s.getTags().isEmpty() ? noFilters : tagsToFilter(s.getTags());
	}

	private Map<String, Integer> tagsToFilter(Set<String> tags) {
		if (tags.size() == 1) {
			return Collections.singletonMap(tags.iterator().next(), 1);
		} else {
			Map<String, Integer> filter = new HashMap<>(tags.size());
			for (String tag : tags) {
				filter.put(tag, 1);
			}
			return filter;
		}
	}

	@Override
	public List<String> getFields() {
		return variants;
	}

	@Override
	public boolean ignoreFilter(String filter) {
		return false;
	}

	@Override
	public String getSearch() {
		return bestQuery;
	}

	@Override
	public String getRealText() {
		return bestQuery;
	}

	@Override
	public Suggestable copy() {
		return new SearchhubSuggest(bestQuery, weight, Collections.unmodifiableList(variants), Collections.unmodifiableMap(filter));
	}

}
