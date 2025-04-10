package de.cxp.ocs.smartsuggest.util;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

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
		if (!Files.exists(filePath)) {
			throw new FileNotFoundException("expected file " + filePath + " not found to be deserialized to " + targetClazz);
		}
		try (
				FileInputStream fileInputStream = new FileInputStream(filePath.toFile());
				ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)
		) {
			return targetClazz.cast(objectInputStream.readObject());
		}
		catch (Exception e) {
			throw new IOException("Can't deserialize type '" + targetClazz.getCanonicalName() + "' from file " + filePath, e);
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

	public static void copyDirectoryRecursively(Path source, Path destination) throws IOException {
		if (!Files.exists(destination)) {
			Files.createDirectory(destination);
		}
		SimpleFileVisitor<Path> copyFileTreeVisitor = new SimpleFileVisitor<>() {

			Path fullTargetPath = destination;

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				// do not copy the name of source root directory
				if (!dir.equals(source)) {
					fullTargetPath = fullTargetPath.resolve(dir.getFileName());
					Files.createDirectory(fullTargetPath);
				}
				return super.preVisitDirectory(dir, attrs);
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				fullTargetPath = fullTargetPath.getParent();
				return super.postVisitDirectory(dir, exc);
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
					throws IOException {
				Files.copy(file, fullTargetPath.resolve(file.getFileName()));
				return FileVisitResult.CONTINUE;
			}
		};
		Files.walkFileTree(source, copyFileTreeVisitor);
	}
}
