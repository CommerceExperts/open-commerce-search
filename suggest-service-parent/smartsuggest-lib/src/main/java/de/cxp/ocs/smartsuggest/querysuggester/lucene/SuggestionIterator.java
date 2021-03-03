package de.cxp.ocs.smartsuggest.querysuggester.lucene;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.util.BytesRef;

import de.cxp.ocs.smartsuggest.spi.SuggestRecord;

abstract class SuggestionIterator implements InputIterator {

	private final static BytesRef EMPTY_PAYLOAD = new BytesRef(SerializationUtils.serialize((Serializable) Collections.emptyMap()));

	private final Iterator<SuggestRecord> innerIterator;

	private SuggestRecord currentSuggestion;

	SuggestionIterator(Iterator<SuggestRecord> innerIterator) {
		this.innerIterator = innerIterator;
	}

	@Override
	public boolean hasContexts() {
		// fuzzy suggester will decline indexing if this is true
		// and the BlendedInfixSuggester does not call this method
		// TODO: to implement context filtering for fuzzy Suggesters, we can
		// append the tags/contexts to the payload object and do the filtering
		// after retrieving fuzzy results
		return false;
	}

	@Override
	public boolean hasPayloads() {
		// we at least have the label as payload
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
		}
		else {
			return null;
		}
	}

	/**
	 * @param suggestion
	 *        The suggestion to index
	 * @return The text to index for the given suggestion
	 */
	protected abstract String getSearchText(SuggestRecord suggestion);

	/**
	 * Serializes the SuggestRecord payload. It will be deserilized and attached
	 * to the returned suggestions again.
	 *
	 * @see LuceneQuerySuggester#getBestMatch(Lookup.LookupResult)
	 * @return A BytesRef with the payload
	 */
	@Override
	public BytesRef payload() {
		Map<String, String> payload = currentSuggestion.getPayload();
		if (payload == null) {
			payload = Collections.singletonMap(LuceneQuerySuggester.PAYLOAD_LABEL_KEY, currentSuggestion.getPrimaryText());
		}
		else {
			payload = new HashMap<>(payload);
			payload.put(LuceneQuerySuggester.PAYLOAD_LABEL_KEY, currentSuggestion.getPrimaryText());
		}
		if (!(payload instanceof Serializable)) {
			payload = new HashMap<>(payload);
		}
		byte[] serialize = SerializationUtils.serialize((Serializable) payload);
		return new BytesRef(serialize);
	}

	// This method returns the contexts for the record, which we can
	// use to restrict suggestions. In this example we use the
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

	// This method helps us order our suggestions. In this example we
	// use the number of products of this type that we've sold.
	@Override
	public long weight() {
		return currentSuggestion.getWeight();
	}
}
