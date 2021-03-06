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
}
