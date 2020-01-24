package de.cxp.ocs.server;

import javax.ws.rs.ApplicationPath;

import org.glassfish.jersey.server.ResourceConfig;

@ApplicationPath("/")
public class ServerApplication extends ResourceConfig {

	public ServerApplication() {
		register(new ServerResource());
	}
}
