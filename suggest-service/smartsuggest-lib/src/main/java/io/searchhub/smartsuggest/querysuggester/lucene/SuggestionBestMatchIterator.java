package io.searchhub.smartsuggest.querysuggester.lucene;

import java.util.Iterator;

import io.searchhub.smartsuggest.spi.SuggestRecord;

class SuggestionBestMatchIterator extends SuggestionIterator {

	SuggestionBestMatchIterator(Iterator<SuggestRecord> innerIterator) {
		super(innerIterator);
	}

	@Override
	protected String getSearchText(SuggestRecord suggestion) {
		return suggestion.getPrimaryText();
	}
}
