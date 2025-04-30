package de.cxp.ocs.smartsuggest.querysuggester.lucene;

import de.cxp.ocs.smartsuggest.spi.SuggestRecord;

import java.util.Iterator;

class SuggestionBestMatchIterator extends SuggestionIterator {

	SuggestionBestMatchIterator(Iterator<SuggestRecord> innerIterator) {
		super(innerIterator);
	}

	@Override
	protected String getSearchText(SuggestRecord suggestion) {
		return suggestion.getPrimaryText();
	}
}
