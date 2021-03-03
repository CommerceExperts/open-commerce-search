package de.cxp.ocs.smartsuggest.querysuggester;

import de.cxp.ocs.smartsuggest.monitoring.Instrumentable;
import de.cxp.ocs.smartsuggest.spi.SuggestData;

public interface SuggesterFactory extends Instrumentable {

	QuerySuggester getSuggester(SuggestData suggestData);

}
