package mindshift.mto.connector.ocs.engine;

import java.util.Map;
import mindshift.mto.connector.ocs.connector.OpenCommerceSearchConnector;
import mindshift.search.connector.api.v2.SearchConnector;
import mindshift.search.connector.api.v2.engine.EngineFactory;

/**
 * Open Commerce Search implementation of the EngineFactory.
 */
public class OpenCommerceSearchEngineFactory implements EngineFactory {

    @Override
    public SearchConnector createConnector(final Map<String, String> map) {
        return new OpenCommerceSearchConnector();
    }
}
