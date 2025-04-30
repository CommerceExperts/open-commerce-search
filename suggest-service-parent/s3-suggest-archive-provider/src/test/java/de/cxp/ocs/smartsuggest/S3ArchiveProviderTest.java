package de.cxp.ocs.smartsuggest;

import de.cxp.ocs.smartsuggest.spi.test.IndexArchiveProviderTestKit;
import io.findify.s3mock.S3Mock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class S3ArchiveProviderTest {

	private static S3Mock                                         s3;
	private static Map<String, Object>                            testConfig;
	static         IndexArchiveProviderTestKit<S3ArchiveProvider> testKit;

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
		testKit = new IndexArchiveProviderTestKit<>(S3ArchiveProvider.class, testConfig);
	}

	@AfterAll
	static void tearDown() {
		if (s3 != null) s3.stop();
	}

	@Test
	void standaloneTest() throws Exception {
		S3ArchiveProvider s3ArchiveProvider = testKit.standaloneTest();
		s3ArchiveProvider.close();
	}

	@Test
	void integrationTest() throws Exception {
		S3ArchiveProvider s3ArchiveProvider = testKit.integrationTest();
		s3ArchiveProvider.close();
	}

	@Test
	void serviceLoaderTest() {
		testKit.serviceLoaderTest();
	}

	@Test
	void integrationTestCompoundIndexArchiver() throws Exception {
		S3ArchiveProvider s3ArchiveProvider = testKit.integrationTestCompoundIndexArchiver();
		s3ArchiveProvider.close();
	}

}
