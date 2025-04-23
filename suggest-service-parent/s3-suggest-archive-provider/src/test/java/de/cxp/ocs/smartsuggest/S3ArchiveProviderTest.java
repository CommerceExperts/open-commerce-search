package de.cxp.ocs.smartsuggest;

import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.Suggestion;
import de.cxp.ocs.smartsuggest.spi.IndexArchive;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestData;
import de.cxp.ocs.smartsuggest.spi.SuggestRecord;
import io.findify.s3mock.S3Mock;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class S3ArchiveProviderTest {

	private static S3Mock              s3;
	private static Map<String, Object> testConfig;

	@BeforeAll
	static void setUp() {
		int port = Util.getFreePort();
		s3 = new S3Mock.Builder().withPort(port).withInMemoryBackend().build();
		s3.start();
		String s3MockEndpoint = "http://localhost:" + port;

		String testBucket = "ocss-test-bucket";
		try (
				S3Client s3Client = S3Client.builder()
						.endpointOverride(URI.create(s3MockEndpoint))
						.forcePathStyle(true)
						.credentialsProvider(AnonymousCredentialsProvider.create())
						.build()
		) {
			s3Client.createBucket(b -> b.bucket(testBucket));
			assertEquals(200, s3Client.headBucket(b -> b.bucket(testBucket)).sdkHttpResponse().statusCode());
		}
		testConfig = Map.of("bucket", testBucket, "_endpoint", s3MockEndpoint, "_forcePathStyle", "true");
	}

	@AfterAll
	static void tearDown() {
		if (s3 != null) s3.stop();
	}

	@Test
	void testStandalone() throws Exception {
		String indexName = "test_1";
		try (S3ArchiveProvider underTest = new S3ArchiveProvider()) {
			underTest.configure(testConfig);
			assert !underTest.hasData(indexName);

			long modTime = System.currentTimeMillis();
			Path tempFile = createTestArchive("xyz");
			underTest.store(indexName, new IndexArchive(tempFile.toFile(), modTime));

			assert underTest.hasData(indexName);
			assert underTest.getLastDataModTime(indexName) == modTime;

			var loadedArchive = underTest.loadData(indexName);
			assertEquals("xyz", unpackContentFromArchive(loadedArchive));
		}
	}

	@Test
	void integrationTest() {
		String indexName2 = "test_2";
		S3ArchiveProvider underTest = new S3ArchiveProvider();

		// first initialize with some data
		SdpMock dataProviderMock = new SdpMock(indexName2)
				.suggestData(SuggestData.builder()
						.modificationTime(System.currentTimeMillis())
						.suggestRecords(List.of(new SuggestRecord("label", "content terms", null, null, 100)))
						.build());
		try (
				var qsm = QuerySuggestManager.builder()
						.withSuggestDataProvider(dataProviderMock)
						.withArchiveDataProvider(underTest)
						.addArchiveProviderConfig(S3ArchiveProvider.class, testConfig)
						.build()
		) {
			assert !underTest.hasData(indexName2);
			assert qsm.getQuerySuggester(indexName2, true).isReady();
			// assert data has been placed
			assert underTest.hasData(indexName2);
		}

		// query suggester and qsm are closed, but data should still be available at the archive
		try (
				var qsm = QuerySuggestManager.builder()
						// without SuggestDataProvider(dataProviderMock)!
						.addArchiveProviderConfig(S3ArchiveProvider.class, testConfig)
						.withArchiveDataProvider(underTest).build()
		) {
			// data should still be available
			assert underTest.hasData(indexName2);
			assert qsm.getQuerySuggester(indexName2, true).isReady();
			QuerySuggester suggester = qsm.getQuerySuggester(indexName2);
			List<Suggestion> suggestions = suggester.suggest("la");
			assertEquals(1, suggestions.size());
		}
	}

	@Test
	void integrationTestWithMultipleDataSources() {
		String indexName3 = "test_3";
		S3ArchiveProvider underTest = new S3ArchiveProvider();

		// first initialize with some data
		long modTime1 = System.currentTimeMillis();
		SdpMock sdp1 = new SdpMock(indexName3)
				.name("sdp1")
				.suggestData(SuggestData.builder()
						.modificationTime(modTime1)
						.suggestRecords(List.of(new SuggestRecord("label 1", "first content", null, null, 100)))
						.build());

		long modTime2 = System.currentTimeMillis();
		SdpMock sdp2 = new SdpMock(indexName3)
				.name("sdp2")
				.suggestData(SuggestData.builder()
						.modificationTime(modTime2)
						.suggestRecords(List.of(new SuggestRecord("label 2", "second content", null, null, 1000)))
						.build());

		SuggestConfig suggestConfig = SuggestConfig.builder().useDataSourceMerger(false).build();
		try (
				var qsm = QuerySuggestManager.builder()
						.withDefaultSuggestConfig(suggestConfig)
						.withSuggestDataProvider(sdp1)
						.withSuggestDataProvider(sdp2)
						.withArchiveDataProvider(underTest)
						.addArchiveProviderConfig(S3ArchiveProvider.class, testConfig)
						.build()
		) {
			assert !underTest.hasData(indexName3);
			assert !underTest.hasData(indexName3 + "/sdp1");
			assert !underTest.hasData(indexName3 + "/sdp2");

			assert qsm.getQuerySuggester(indexName3, true).isReady();
			// assert data has been placed
			assert underTest.hasData(indexName3 + "/sdp1");
			assert underTest.hasData(indexName3 + "/sdp2");
		}

		// now run again without the sdp mocks
		try (
				var qsm = QuerySuggestManager.builder()
						.withDefaultSuggestConfig(suggestConfig)
						.withArchiveDataProvider(underTest)
						.addArchiveProviderConfig(S3ArchiveProvider.class, testConfig)
						.build()
		) {
			assert underTest.hasData(indexName3 + "/sdp1");
			assert underTest.hasData(indexName3 + "/sdp2");

			var suggester = qsm.getQuerySuggester(indexName3, true);
			assert suggester.isReady();
			List<Suggestion> suggestions = suggester.suggest("lab");
			assertEquals(2, suggestions.size());
			assertEquals("label 2", suggestions.get(0).getLabel()); // has higher weight
			assertEquals("label 1", suggestions.get(1).getLabel());
		}
	}

	private static Path createTestArchive(String content) throws IOException {
		Path tempFile = Files.createTempFile("test-archive.", ".tar.gz");
		Path contentFile = Files.createTempFile("content.", ".txt");
		Files.writeString(contentFile, content, StandardCharsets.UTF_8);

		try (
				OutputStream fileOutputStream = Files.newOutputStream(tempFile);
				BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
				GZIPOutputStream gzipOutputStream = new GZIPOutputStream(bufferedOutputStream);
				TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(gzipOutputStream)
		) {

			tarOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

			TarArchiveEntry entry = new TarArchiveEntry(contentFile.toFile(), "content.txt");
			tarOutputStream.putArchiveEntry(entry);
			Files.copy(contentFile, tarOutputStream);
			tarOutputStream.closeArchiveEntry();
		}
		return tempFile;
	}

	public static String unpackContentFromArchive(IndexArchive archive) throws IOException {
		File tarFile = archive.zippedTarFile();

		Path targetFolder = Files.createTempDirectory("test-unpack-archive");

		try (
				InputStream fileInputStream = Files.newInputStream(tarFile.toPath());
				BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
				GZIPInputStream gzipInputStream = new GZIPInputStream(bufferedInputStream);
				TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzipInputStream)
		) {

			TarArchiveEntry entry;
			while ((entry = tarInputStream.getNextEntry()) != null) {
				Path entryPath = targetFolder.resolve(entry.getName());

				if (entry.isDirectory()) {
					Files.createDirectories(entryPath);
				}
				else {
					Path parent = entryPath.getParent();
					if (parent != null && !Files.exists(parent)) {
						Files.createDirectories(parent);
					}
					Files.copy(tarInputStream, entryPath);
				}
			}
		}

		Path resolve = targetFolder.resolve("content.txt");
		assert Files.exists(resolve);
		return Files.readString(resolve);
	}

}
