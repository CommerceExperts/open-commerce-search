package de.cxp.ocs.smartsuggest.spi.test;

import de.cxp.ocs.smartsuggest.QuerySuggestManager;
import de.cxp.ocs.smartsuggest.querysuggester.QuerySuggester;
import de.cxp.ocs.smartsuggest.querysuggester.Suggestion;
import de.cxp.ocs.smartsuggest.spi.*;
import de.cxp.ocs.smartsuggest.util.FileUtils;
import lombok.RequiredArgsConstructor;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;

/**
 * <p>
 * This is a set of different tests, that each IndexArchiveProvider implementation should pass.
 * It can easily be used in the testing context of the according implementation.
 * </p>
 * <p>
 * Most methods return the tested instance, so you can do more assertions.
 * </p>
 *
 * @param <T>
 */
@RequiredArgsConstructor
public class IndexArchiveProviderTestKit<T extends IndexArchiveProvider> {

	private final Class<T>            classUnderTest;
	private final Map<String, Object> testConfig;

	public void testAll() throws Exception {
		standaloneTest();
		integrationTest();
		serviceLoaderTest();
		if (CompoundIndexArchiveProvider.class.isAssignableFrom(classUnderTest)) {
			integrationTestCompoundIndexArchiver();
		}
	}

	public T standaloneTest() throws Exception {
		T underTest = classUnderTest.getConstructor().newInstance();
		try {
			String indexName = "test_1";
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
		catch (Exception e) {
			if (underTest instanceof Closeable closable) {
				closable.close();
			}
			throw e;
		}
		return underTest;
	}

	public T integrationTest() throws Exception {
		String indexName2 = "test_2";
		T underTest = classUnderTest.getConstructor().newInstance();

		try {
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
							.addArchiveProviderConfig(classUnderTest, testConfig)
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
							.addArchiveProviderConfig(classUnderTest, testConfig)
							.withArchiveDataProvider(underTest)
							.build()
			) {
				// data should still be available
				assert underTest.hasData(indexName2);
				assert qsm.getQuerySuggester(indexName2, true).isReady();
				QuerySuggester suggester = qsm.getQuerySuggester(indexName2);
				List<Suggestion> suggestions = suggester.suggest("la");
				assertEquals(1, suggestions.size());
			}
		}
		catch (Exception e) {
			if (underTest instanceof Closeable closable) {
				closable.close();
			}
			throw e;
		}

		return underTest;
	}

	public void serviceLoaderTest() {
		ServiceLoader<IndexArchiveProvider> loadedArchiveProviders = ServiceLoader.load(IndexArchiveProvider.class);
		assert loadedArchiveProviders.stream()
				.map(Provider::get)
				.peek(iap -> System.out.println("Found IndexArchiveProvider " + iap.getClass().getCanonicalName()))
				.anyMatch(iap -> classUnderTest.isAssignableFrom(iap.getClass())) : classUnderTest.getCanonicalName() + " not found via ServiceLoader";
	}

	/**
	 * Only run this test, if your implementation extends the {@link CompoundIndexArchiveProvider} class.
	 *
	 * @return the tested instance
	 * @throws Exception
	 * 		in case of some unexpected behaviour
	 * @throws AssertionError
	 * 		if test failed
	 */
	public T integrationTestCompoundIndexArchiver() throws Exception {
		if (!CompoundIndexArchiveProvider.class.isAssignableFrom(classUnderTest)) {
			throw new AssertionError("this test only works, if the according ");
		}

		String indexName3 = "test_3";
		T underTest = classUnderTest.getConstructor().newInstance();

		try {
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
							.addArchiveProviderConfig(classUnderTest, testConfig)
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
							.addArchiveProviderConfig(classUnderTest, testConfig)
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
		catch (Exception e) {
			if (underTest instanceof Closeable closable) {
				closable.close();
			}
			throw e;
		}
		return underTest;
	}

	private static Path createTestArchive(String content) throws IOException {
		Path archiveDir = Files.createTempDirectory("test-archive");
		Path contentFile = archiveDir.resolve("content.txt");
		Files.writeString(contentFile, content, StandardCharsets.UTF_8);
		return FileUtils.packArchive(archiveDir, "test-archive.").toPath();
	}

	private static String unpackContentFromArchive(IndexArchive archive) throws IOException {
		Path targetFolder = Files.createTempDirectory("test-unpack-archive");
		FileUtils.unpackArchive(archive, targetFolder);
		Path resolve = targetFolder.resolve("content.txt");
		assert Files.exists(resolve);
		return Files.readString(resolve);
	}

	private static void assertEquals(String expected, String actual) {
		if (!expected.equals(actual)) {
			throw new AssertionError("unexpected value '" + actual + "' - expected value " + expected);
		}
	}

	private static void assertEquals(int expected, int actual) {
		if (expected != actual) {
			throw new AssertionError("unexpected value '" + actual + "' - expected value " + expected);
		}
	}
}
