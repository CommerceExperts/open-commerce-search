package mindshift.search.connector.ocs.adapter;

import mindshift.search.connector.api.v2.models.SuggestRequest;
import mindshift.search.connector.api.v2.models.SuggestResult;

/**
 * Open Commerce Search - Suggest Services.
 */
public class OcsSuggestServiceAdapter {

    /**
     * Performs suggest for a given SuggestRequest.
     * 
     * @param request SuggestRequest
     * @return SuggestResult
     */
    public SuggestResult suggest(final SuggestRequest request) {
        // TODO OCS has no suggest API yet

        SuggestResult suggestResult = new SuggestResult();
        suggestResult.setQ(request.getQ());
        return suggestResult;
    }
}
