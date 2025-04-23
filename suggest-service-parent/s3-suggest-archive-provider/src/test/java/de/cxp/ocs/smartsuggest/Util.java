package de.cxp.ocs.smartsuggest;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;

public class Util {

	public static int getFreePort() {
		Random r = new Random();

		int foundPort = 0;
		while (foundPort == 0) {
			int randomPort = 41000 + r.nextInt(1000);
			try (ServerSocket serverSocket = new ServerSocket(randomPort)) {
				if (serverSocket.getLocalPort() == randomPort) {
					foundPort = randomPort;
				}
			}
			catch (IOException e) {
				// ignore, search another port
			}
		}
		return foundPort;
	}
}
