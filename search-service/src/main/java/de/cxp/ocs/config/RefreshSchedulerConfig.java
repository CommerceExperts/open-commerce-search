package de.cxp.ocs.config;

import de.cxp.ocs.SearchController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * <p>Schedulers to refresh configuration regularly.</p>
 *
 * <p>set property 'ocs.scheduler.enabled=false' to disable completely.</p>
 *
 * <p>
 * To enable refresh config, set the property 'ocs.scheduler.enabled.refresh-config=true'.
 * The property 'ocs.scheduler.refresh-config-delay-ms' can be set to configure the fixed delay between each refresh (defaults to 60000 = 1 minute)
 * </p>
 * <p>
 * To enable context refreshing, set the property 'ocs.scheduler.enabled.refresh-context=true'.
 * The property 'ocs.scheduler.refresh-context-delay-ms' can be set to configure the fixed delay between each refresh (defaults to 60000 = 1 minute)
 * </p>
 * <p>
 * Be careful about this configuration, since activating both schedulers leads to an endless reloading of everything.
 * </p>
 */
@Component
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "ocs.scheduler.enabled")
public class RefreshSchedulerConfig {

	@Autowired
	private SearchController controller;

	@Autowired
	private ContextRefresher contextRefresher;

	@ConditionalOnProperty(name = "ocs.scheduler.enabled.refresh-config")
	@Scheduled(fixedDelayString = "${ocs.scheduler.refresh-config-delay-ms:60000}")
	public void searchControllerRefreshConfigs() {
		controller.refreshAllConfigs();
	}

	@ConditionalOnProperty(name = "ocs.scheduler.enabled.refresh-context")
	@Scheduled(fixedDelayString = "${ocs.scheduler.refresh-context-delay-ms:60000}")
	public void refreshContext() {
		contextRefresher.refresh();
	}

}
