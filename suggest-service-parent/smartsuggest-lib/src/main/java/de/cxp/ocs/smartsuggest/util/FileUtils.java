package de.cxp.ocs.smartsuggest.util;

import de.cxp.ocs.smartsuggest.spi.IndexArchive;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

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

	public static boolean isTarGz(File file) {
		if (file == null || !file.exists() || !file.isFile()) {
			return false;
		}
		
		// Check file extension
		String name = file.getName().toLowerCase();
		if (!name.endsWith(".tar.gz") && !name.endsWith(".tgz")) {
			return false;
		}
		
		// Check magic numbers for gzip format
		try (FileInputStream fis = new FileInputStream(file)) {
			byte[] magic = new byte[2];
			if (fis.read(magic) != 2) {
				return false;
			}
			// Check for gzip magic number: 0x1f 0x8b
			return (magic[0] & 0xff) == 0x1f && (magic[1] & 0xff) == 0x8b;
		}
		catch (IOException e) {
			return false;
		}
	}

	public static void unpackArchive(IndexArchive archive, Path targetFolder) throws IOException {
		File tarFile = archive.zippedTarFile();
		if (!isTarGz(tarFile)) {
			throw new IllegalArgumentException("File " + tarFile + " is not a valid tar.gz archive");
		}

		if (!Files.exists(targetFolder)) {
			Files.createDirectories(targetFolder);
		}

		try (InputStream fileInputStream = Files.newInputStream(tarFile.toPath());
			 BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
			 GZIPInputStream gzipInputStream = new GZIPInputStream(bufferedInputStream);
			 TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzipInputStream)) {

			TarArchiveEntry entry;
			while ((entry = tarInputStream.getNextEntry()) != null) {
				Path entryPath = targetFolder.resolve(entry.getName());
				
				if (entry.isDirectory()) {
					Files.createDirectories(entryPath);
				} else {
					Path parent = entryPath.getParent();
					if (parent != null && !Files.exists(parent)) {
						Files.createDirectories(parent);
					}
					Files.copy(tarInputStream, entryPath);
				}
			}
		}
	}

	public static File packArchive(Path sourceFolder, String prefix) throws IOException {
		Path tempFile = Files.createTempFile(prefix, ".tar.gz");
		
		try (OutputStream fileOutputStream = Files.newOutputStream(tempFile);
			 BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
			 GZIPOutputStream gzipOutputStream = new GZIPOutputStream(bufferedOutputStream);
			 TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(gzipOutputStream)) {
			
			tarOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
			
			Files.walkFileTree(sourceFolder, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					String entryName = sourceFolder.relativize(file).toString();
					TarArchiveEntry entry = new TarArchiveEntry(file.toFile(), entryName);
					tarOutputStream.putArchiveEntry(entry);
					Files.copy(file, tarOutputStream);
					tarOutputStream.closeArchiveEntry();
					return FileVisitResult.CONTINUE;
				}
			});
		}

		return tempFile.toFile();
	}
}
