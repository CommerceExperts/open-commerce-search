package de.cxp.ocs.smartsuggest.querysuggester.lucene;

import java.util.Iterator;

import de.cxp.ocs.smartsuggest.spi.SuggestRecord;

class SuggestionBestMatchIterator extends SuggestionIterator {

	SuggestionBestMatchIterator(Iterator<SuggestRecord> innerIterator) {
		super(innerIterator);
	}

	@Override
	protected String getSearchText(SuggestRecord suggestion) {
		return suggestion.getPrimaryText();
	}
}
