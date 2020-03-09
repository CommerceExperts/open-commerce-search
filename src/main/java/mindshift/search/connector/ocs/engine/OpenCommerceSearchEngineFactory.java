package mindshift.search.connector.ocs.engine;

import java.util.Map;

import mindshift.search.connector.api.v2.SearchConnector;
import mindshift.search.connector.api.v2.engine.EngineFactory;
import mindshift.search.connector.ocs.connector.OpenCommerceSearchConnector;

/**
 * Open Commerce Search implementation of the EngineFactory.
 */
public class OpenCommerceSearchEngineFactory implements EngineFactory {

    @Override
    public SearchConnector createConnector(final Map<String, String> map) {
        return new OpenCommerceSearchConnector();
    }
}
