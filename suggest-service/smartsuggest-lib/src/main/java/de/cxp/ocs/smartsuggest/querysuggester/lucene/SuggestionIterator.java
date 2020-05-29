package de.cxp.ocs.smartsuggest.querysuggester.lucene;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.util.BytesRef;

import de.cxp.ocs.smartsuggest.spi.SuggestRecord;


abstract class SuggestionIterator implements InputIterator
{
    private final Iterator<SuggestRecord> innerIterator;

    private SuggestRecord currentSuggestion;

    SuggestionIterator(Iterator<SuggestRecord> innerIterator) {
        this.innerIterator = innerIterator;
    }

    @Override
    public boolean hasContexts() {
        return currentSuggestion != null && !currentSuggestion.getTags().isEmpty();
    }

    @Override
    public boolean hasPayloads() {
        return true;
    }

    // This method needs to return the key for the record; this is the
    // text we'll be auto-completing against.
    @Override
    public BytesRef next() {
        if (innerIterator.hasNext()) {
            currentSuggestion = innerIterator.next();
            String term = getSearchText(currentSuggestion);
            return new BytesRef(term.getBytes(StandardCharsets.UTF_8));
        } else {
            return null;
        }
    }

    /**
     * @param suggestion
     *      The suggestion to index
     * @return The text to index for the given suggestion
     */
    protected abstract String getSearchText(SuggestRecord suggestion);

    /**
     * Return the best query as a payload.
     * Later it is used as a suggestion for any match on a variant
     *
     * @see LuceneQuerySuggester#getBestMatch(Lookup.LookupResult)
     * @return A BytesRef with the bestMatch
     */
    @Override
    public BytesRef payload() {
    	String payload = currentSuggestion.getPayload();
    	if (payload == null) {
    		payload = currentSuggestion.getPrimaryText();
    	}
        final byte[] payloadData = payload.getBytes(StandardCharsets.UTF_8);
        return new BytesRef(payloadData);
    }

    // This method returns the contexts for the record, which we can
    // use to restrict suggestions.  In this example we use the
    // regions in which a product is sold.
    @Override
    public Set<BytesRef> contexts() {
    	if (currentSuggestion.getTags() == null || currentSuggestion.getTags().isEmpty()) {
    		return Collections.emptySet();
    	}
    	
        Set<BytesRef> contexts = new HashSet<>();
        for (String context : currentSuggestion.getTags()) {
            contexts.add(new BytesRef(context.getBytes(StandardCharsets.UTF_8)));
        }
        return contexts;
    }

    // This method helps us order our suggestions.  In this example we
    // use the number of products of this type that we've sold.
    @Override
    public long weight() {
        return currentSuggestion.getWeight();
    }
}
