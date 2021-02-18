package de.cxp.ocs.smartsuggest.util;

import java.util.*;
import java.util.regex.Pattern;

import de.cxp.ocs.smartsuggest.querysuggester.*;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FakeSuggester implements QuerySuggester {

	private final SuggestRecord[] records;

	@Override
	public List<Suggestion> suggest(String term, int maxResults, Set<String> tags) throws SuggestException {
		List<Suggestion> result = new ArrayList<>(Math.min(maxResults, records.length));

		for (SuggestRecord record : records) {
			if (!tags.isEmpty() && !tags.stream().anyMatch(record.getTags()::contains)) {
				continue;
			}
			if (record.getPrimaryText().startsWith(term)) {
				result.add(toSuggestion(record));
			}
			else if (record.getSecondaryText().matches("\\b" + Pattern.quote(term))) {
				result.add(toSuggestion(record).setWeight(record.getWeight() / 2));
			}
			if (result.size() == maxResults) break;
		}

		Collections.sort(result, Comparator.comparingLong(Suggestion::getWeight).reversed());

		return result;
	}

	private Suggestion toSuggestion(SuggestRecord record) {
		return new Suggestion(record.getPrimaryText()).setPayload(record.getPayload()).setWeight(record.getWeight());
	}

	@Override
	public boolean isReady() {
		return true;
	}

	@Override
	public void close() throws Exception {}
}
