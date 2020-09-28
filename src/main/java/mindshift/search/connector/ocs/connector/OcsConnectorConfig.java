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

	public static final String	SEARCH_SERVICE_ENDPOINT		= "ocs.search.endpoint";
	public static final String	SUGGEST_SERVICE_ENDPOINT	= "ocs.suggest.endpoint";

	public static final String	AUTH_USER		= "ocs.auth.user";
	public static final String	AUTH_PASSWORD	= "ocs.auth.password";

	/**
	 * Constructor from map data.
	 * 
	 * @param data
	 */
	public OcsConnectorConfig(final Map<String, String> data) {
		super(data);
	}

	/**
	 * Constructor from properties.
	 * 
	 * @param properties
	 */
	public OcsConnectorConfig(final Properties properties) {
		super(properties);
	}

	/**
	 * Constructor from stream.
	 * 
	 * @param stream
	 */
	public OcsConnectorConfig(final InputStream stream) {
		super(stream);
	}

	public String getSearchEndpoint() {
		return getRequiredProperty(SEARCH_SERVICE_ENDPOINT);
	}

	public String getSuggestEndpoint() {
		return getRequiredProperty(SUGGEST_SERVICE_ENDPOINT);
	}

	public String getAuthUser() {
		return getRequiredProperty(AUTH_USER);
	}

	public String getAuthPassword() {
		return getRequiredProperty(AUTH_PASSWORD);
	}
}
