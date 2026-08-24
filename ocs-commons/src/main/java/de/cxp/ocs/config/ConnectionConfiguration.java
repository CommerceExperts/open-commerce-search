package de.cxp.ocs.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConnectionConfiguration {

	/**
	 * Sets the hosts to where Elasticsearch is running, comma separated for
	 * multiple hosts.
	 */
	@NonNull
	private String hosts;

	/**
	 * If required, username + password are expected, separated by colon.
	 * Example: "elastic:my$ecretPassw0rd"
	 */
	private String auth;

	/**
	 * If set to 'true' the rest-high-level-client version 7 can be used with elasticsearch version 8.
	 * <a href="https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/java-rest-high-compatibility.html">see Compatibility with Elasticsearch 8.x</a>
	 */
	private boolean useCompatibilityMode = false;
}
