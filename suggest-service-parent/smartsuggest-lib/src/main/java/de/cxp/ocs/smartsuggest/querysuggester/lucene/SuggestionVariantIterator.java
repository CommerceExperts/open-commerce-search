package de.cxp.ocs.smartsuggest.querysuggester.lucene;

import de.cxp.ocs.smartsuggest.spi.SuggestRecord;

import java.util.Iterator;

class SuggestionVariantIterator extends SuggestionIterator {

	private final static String	EMPTY			= "";
	private final static int	MaxTermLength	= 32000;

	SuggestionVariantIterator(Iterator<SuggestRecord> innerIterator) {
		super(innerIterator);
	}

	@Override
	protected String getSearchText(SuggestRecord suggestion) {
		String searchText = suggestion.getSecondaryText();
		if (searchText == null) {
			searchText = EMPTY;
		}
		else if (searchText.length() > MaxTermLength) {
			searchText = searchText.substring(0, MaxTermLength);
		}
		return searchText;
	}

}
