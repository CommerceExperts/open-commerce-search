package de.cxp.ocs.smartsuggest.spi;

import de.cxp.ocs.smartsuggest.spi.test.IndexArchiveProviderTestKit;
import de.cxp.ocs.smartsuggest.updater.LocalCompoundIndexArchiveProvider;
import de.cxp.ocs.smartsuggest.updater.LocalIndexArchiveProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

public class IndexArchiveProviderTestKitTest {

	@Test
	void withLocalIndexArchiveProvider() throws Exception {
		IndexArchiveProviderTestKit<LocalIndexArchiveProvider> testKit = new IndexArchiveProviderTestKit<>(LocalIndexArchiveProvider.class, Map.of());
		testKit.standaloneTest();
		testKit.integrationTest();
		//testKit.serviceLoaderTest(); test class not exposed via META-INF/services
	}

	@Test
	void withLocalCompoundIndexArchiveProvider() throws Exception {
		IndexArchiveProviderTestKit<LocalCompoundIndexArchiveProvider> testKit = new IndexArchiveProviderTestKit<>(LocalCompoundIndexArchiveProvider.class, Map.of());
		testKit.standaloneTest();
		testKit.integrationTest();
		// testKit.serviceLoaderTest(); test class not exposed via META-INF/services
		assert testKit.integrationTestCompoundIndexArchiver() != null;
	}

	@Test
	public void testAll() {
		try {
			IndexArchiveProviderTestKit<LocalIndexArchiveProvider> testKit = new IndexArchiveProviderTestKit<>(LocalIndexArchiveProvider.class, Map.of());
			testKit.testAll();
			fail("expecting failure due to missing service loader test");
		}
		catch (Throwable e) {
			assert e instanceof AssertionError;
		}
	}
}
