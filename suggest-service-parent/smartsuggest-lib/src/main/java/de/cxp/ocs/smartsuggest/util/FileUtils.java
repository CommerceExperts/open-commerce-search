package de.cxp.ocs.smartsuggest.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtils {

	public static void persistSerializable(Path filePath, Serializable serializable) throws IOException {
		try (
				FileOutputStream fileStream = new FileOutputStream(filePath.toFile());
				ObjectOutputStream objectStream = new ObjectOutputStream(fileStream)
		) {
			objectStream.writeObject(serializable);
		}
	}

	public static <T> T loadSerializable(Path filePath, Class<T> targetClazz) throws IOException {
		try (
				FileInputStream fileInputStream = new FileInputStream(filePath.toFile());
				ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
		) {
			return targetClazz.cast(objectInputStream.readObject());
		}
		catch (Exception e) {
			throw new IOException("Can't deserialize type '"+targetClazz.getCanonicalName()+"' from file "+filePath, e);
		}

	}

	public static boolean isEmptyDirectory(Path indexFolder) {
		try (var fileStream = Files.list(indexFolder)) {
			return fileStream.findAny().isEmpty();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
