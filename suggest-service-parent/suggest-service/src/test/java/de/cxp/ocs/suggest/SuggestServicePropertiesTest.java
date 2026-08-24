package de.cxp.ocs.suggest;

import java.nio.file.Path;
import java.util.*;

import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestConfig.SortStrategy;
import org.junit.jupiter.api.Test;

import de.cxp.ocs.smartsuggest.spi.SuggestConfig.GroupConfig;

import static org.junit.jupiter.api.Assertions.*;

public class SuggestServicePropertiesTest {

    @Test
    public void testLegacyPropertiesNamesStillWork() {
		Map<String, String> envMock = new HashMap<>();
		envMock.put("SUGGEST_GROUP_PREFETCH_LIMIT_FACTOR", "5");

		Properties props = new Properties();
		props.setProperty("suggest.group.share.conf", "brand=0.4,category=0.3");
		props.setProperty("suggester.max.idle.minutes", "27");
		props.setProperty("suggest.data.source.merger", "true");
		props.setProperty("suggest.mgmt.path.prefix", "/foo");

		SuggestServiceProperties underTest = new SuggestServiceProperties(props, envMock::get);

		assertEquals(5, underTest.getDefaultSuggestConfig().getPrefetchLimitFactor());

        List<GroupConfig> groupConfig = underTest.getDefaultSuggestConfig().getGroupConfig();
        assertEquals(2, groupConfig.size());
		assertTrue(groupConfig.stream().anyMatch(c -> c.getGroupName().equals("brand") && c.getLimit() == 40));
		assertTrue(groupConfig.stream().anyMatch(c -> c.getGroupName().equals("category") && c.getLimit() == 30));

        assertEquals(27, underTest.getSuggesterMaxIdleMinutes());

        assertTrue(underTest.getDefaultSuggestConfig().isUseDataSourceMerger());

		assertEquals("/foo", underTest.getManagementPathPrefix());

    }

	@Test
	public void testDefaultValues() {
		SuggestServiceProperties underTest = new SuggestServiceProperties();
		assertEquals(8081, underTest.getServerPort());
		assertEquals("0.0.0.0", underTest.getServerAdress());
		assertEquals(30, underTest.getSuggesterMaxIdleMinutes());
		assertEquals("", underTest.getManagementPathPrefix());
		assertEquals(60, underTest.getUpdateRateInSeconds());
		assertEquals(0, underTest.getPreloadIndexes().length);

		Path indexFolder = underTest.getIndexFolder();
		assertNotNull(indexFolder);
		assertTrue(indexFolder.startsWith("/tmp") || indexFolder.toString().contains("\\Temp\\"), "should be a temp folder, but was "+indexFolder);

		SuggestConfig suggestConfig = underTest.getDefaultSuggestConfig();
		assertEquals(Locale.ROOT, suggestConfig.getLocale());
		assertNull(suggestConfig.getGroupKey());
		assertFalse(suggestConfig.isUseRelativeShareLimit());
		assertEquals(0, suggestConfig.getGroupConfig().size());
		assertEquals(0, suggestConfig.getGroupDeduplicationOrder().length);
		assertEquals(1, suggestConfig.getPrefetchLimitFactor());
		assertEquals(12, suggestConfig.getMaxSharpenedQueries());
		assertFalse(suggestConfig.isAlwaysDoFuzzy());
		assertEquals(SortStrategy.PrimaryAndSecondaryByWeight, suggestConfig.getSortStrategy());
		assertTrue(suggestConfig.isIndexConcurrently());
	}
	
}
