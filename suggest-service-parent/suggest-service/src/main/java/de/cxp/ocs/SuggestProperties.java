package de.cxp.ocs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.Optional;

public class SuggestProperties {

	public int getServerPort() {
		return Optional.ofNullable(System.getenv("SUGGEST_SERVER_PORT"))
				.map(Integer::parseInt)
				.orElse(8081);
	}

	public String getServerAdress() {
		return Optional.ofNullable(System.getenv("SUGGEST_SERVER_ADDRESS"))
				.orElse("0.0.0.0");
	}

	public int getUpdateRateInSeconds() {
		return Optional.ofNullable(System.getenv("SUGGEST_UPDATE_RATE"))
				.map(Integer::parseInt)
				.orElse(60);
	}

	public String[] getPreloadIndexes() {
		return Optional.ofNullable(System.getenv("SUGGEST_PRELOAD_INDEXES"))
				.map(s -> s.split(","))
				.orElse(new String[0]);
	}

	public Path getIndexFolder() {
		String indexFolder = System.getenv("SUGGEST_INDEX_FOLDER");
		if (indexFolder != null) {
			return Paths.get(indexFolder);
		} else {
			try {
				return Files.createTempDirectory("ocs_suggest");
			}
			catch (IOException e) {
				throw new UncheckedIOException("Could not create temporary suggest index directory", e);
			}
		}
	}
}
