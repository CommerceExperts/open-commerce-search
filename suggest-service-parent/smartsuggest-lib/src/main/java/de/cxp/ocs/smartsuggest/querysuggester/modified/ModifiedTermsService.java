package de.cxp.ocs.smartsuggest.querysuggester.modified;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

import lombok.RequiredArgsConstructor;

/**
 * A service that provides mappings of modified (relaxed or sharpened) queries.
 */
@RequiredArgsConstructor
public class ModifiedTermsService implements Accountable {

    private final Map<String, List<String>> relaxedTerms;
    private final Map<String, List<String>> sharpenedTerms;

    private final List<String> emptyList = Collections.emptyList();
    
    public List<String> getRelaxedTerm(String term) {
        return relaxedTerms != null ? relaxedTerms.getOrDefault(term, emptyList) : emptyList;
    }

    public List<String> getSharpenedTerm(String term) {
        return sharpenedTerms != null ? sharpenedTerms.getOrDefault(term, emptyList) : emptyList;
    }
    
    public boolean hasData() {
    	return relaxedTerms != null && !relaxedTerms.isEmpty() || sharpenedTerms != null && !sharpenedTerms.isEmpty();
    }

	@Override
	public long ramBytesUsed() {
		long mySize = RamUsageEstimator.shallowSizeOf(this);
		mySize += RamUsageEstimator.sizeOfMap(relaxedTerms);
		mySize += RamUsageEstimator.sizeOfMap(sharpenedTerms);
		return mySize;
	}
}
