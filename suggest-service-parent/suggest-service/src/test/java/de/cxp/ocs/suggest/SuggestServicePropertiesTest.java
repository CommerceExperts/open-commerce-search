package de.cxp.ocs.suggest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.smartsuggest.spi.SuggestConfig.GroupConfig;

public class SuggestServicePropertiesTest {

    @Test
    public void testLegacyPropertiesNamesStillWork() {
		Map<String, String> envMock = new HashMap<>();
        Properties props = new Properties();
		SuggestServiceProperties underTest = new SuggestServiceProperties(props, envMock::get);

		envMock.put("SUGGEST_GROUP_PREFETCH_LIMIT_FACTOR", "5");
		assertEquals(5, underTest.getDefaultSuggestConfig().getPrefetchLimitFactor());

		props.setProperty("suggest.group.share.conf", "brand=0.4,category=0.3");
        List<GroupConfig> groupConfig = underTest.getDefaultSuggestConfig().getGroupConfig();
        assertEquals(2, groupConfig.size());
		assertTrue(groupConfig.stream().anyMatch(c -> c.getGroupName().equals("brand") && c.getLimit() == 40));
		assertTrue(groupConfig.stream().anyMatch(c -> c.getGroupName().equals("category") && c.getLimit() == 30));

		props.setProperty("suggester.max.idle.minutes", "27");
        assertEquals(27, underTest.getSuggesterMaxIdleMinutes());

        props.setProperty("suggest.data.source.merger", "true");
        assertTrue(underTest.getDefaultSuggestConfig().isUseDataSourceMerger());

		props.setProperty("suggest.mgmt.path.prefix", "/foo");
		assertEquals("/foo", underTest.getManagementPathPrefix());

    }
	
}
