package de.cxp.ocs.server;

import java.io.IOException;
import java.net.URI;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;

public class ServerTest {
	
	public static void main(String[] args) {
		start();
	}
	
	public static void start() throws IllegalStateException {
		new Thread(() -> {
			try {
				URI baseURI = URI.create("http://localhost:9000");
				
				ServerApplication application = new ServerApplication();
				HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseURI, application, false);
				Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow));
				server.start();

				System.out.println("Started HTTP Search server at " + baseURI);

				Thread.currentThread().join();
			} catch (IOException | InterruptedException ex) {
				System.err.println("Error in HTTP service component, process exiting" + ex);
			}		
		}).start();
	}
}