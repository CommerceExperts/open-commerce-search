package mindshift.search.connector.ocs.connector;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import mindshift.search.connector.api.v2.ConnectorConfigSkeleton;

/**
 * config for the Open-Commerce-Search connector.
 * 
 * @author Rudolf Batt
 */
public class OcsConnectorConfig extends ConnectorConfigSkeleton<OpenCommerceSearchConnector> {

    private static final long serialVersionUID = -6152063938985309750L;

    public static final String SEARCH_SERVICE_ENDPOINT = "ocs.search.endpoint";
    
    public static final String SUGGEST_SERVICE_ENDPOINT = "ocs.suggest.endpoint";

	public OcsConnectorConfig(Map<String, String> data) {
		super(data);
	}

	public OcsConnectorConfig(Properties properties) {
		super(properties);
	}
    
    public OcsConnectorConfig(InputStream stream) {
    	super(stream);
	}

	public String getSearchEndpoint() {
    	return getRequiredProperty(SEARCH_SERVICE_ENDPOINT);
    }

	public String getSuggestEndpoint() {
    	return getRequiredProperty(SUGGEST_SERVICE_ENDPOINT);
    }
}
