package io.searchhub.smartsuggest.querysuggester.lucene;

import java.util.Iterator;

import io.searchhub.smartsuggest.spi.SuggestRecord;

class SuggestionVariantIterator extends SuggestionIterator
{

	private final static int MaxTermLength = 32000;

    SuggestionVariantIterator(Iterator<SuggestRecord> innerIterator) {
        super(innerIterator);
    }

    @Override
    protected String getSearchText(SuggestRecord suggestion) {
    	String searchText = suggestion.getSecondaryText();
    	if (searchText.length() > MaxTermLength) {
    		searchText = searchText.substring(0, MaxTermLength);
    	}
    	return searchText;
    }

}
