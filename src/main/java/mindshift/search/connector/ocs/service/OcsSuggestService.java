package mindshift.search.connector.ocs.service;

import mindshift.search.connector.api.v2.models.SuggestRequest;
import mindshift.search.connector.api.v2.models.SuggestResult;

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
        // TODO get results from OCS

        SuggestResult suggestResult = new SuggestResult();
        suggestResult.setQ(request.getQ());
        return suggestResult;
    }
}
