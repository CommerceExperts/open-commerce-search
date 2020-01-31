package mindshift.mto.connector.ocs.service;

import mindshift.search.connector.models.SuggestRequest;
import mindshift.search.connector.models.SuggestResult;

/**
 * Open Commerce Search - Suggest Services.
 */
public class OcsSuggestService {

    /**
     * Performs suggest for a given SuggestRequest.
     * 
     * @param request SuggestRequest
     * @return SuggestResult
     */
    public SuggestResult suggest(final SuggestRequest request) {
        SuggestResult suggestResult = new SuggestResult();
        suggestResult.setQ(request.getQ());
        return suggestResult;
    }
}
