package mindshift.search.connector.ocs.engine;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import mindshift.search.connector.api.v2.Connector.Metadata;
import mindshift.search.connector.api.v2.ConnectorFactorySkeleton;
import mindshift.search.connector.api.v2.FactoryException;
import mindshift.search.connector.ocs.connector.OcsConnectorConfig;
import mindshift.search.connector.ocs.connector.OpenCommerceSearchConnector;

/**
 * Open Commerce Search implementation of the EngineFactory.
 */
public class OpenCommerceSearchEngineFactory
        extends ConnectorFactorySkeleton<OpenCommerceSearchConnector, OcsConnectorConfig> {

    @Override
    public Class<OpenCommerceSearchConnector> managedType() {
        return OpenCommerceSearchConnector.class;
    }

    @Override
    protected OpenCommerceSearchConnector create(final Metadata metadata,
            final OcsConnectorConfig config) throws FactoryException {
        return new OpenCommerceSearchConnector(metadata, config);
    }

    @Override
    protected OcsConnectorConfig createConfiguration(final Map<String, String> args) {
        return new OcsConnectorConfig(args);
    }

    @Override
    protected OcsConnectorConfig createConfiguration(final Properties properties) {
        return new OcsConnectorConfig(properties);
    }

    @Override
    protected OcsConnectorConfig createConfiguration(final InputStream stream) {
        return new OcsConnectorConfig(stream);
    }
}
