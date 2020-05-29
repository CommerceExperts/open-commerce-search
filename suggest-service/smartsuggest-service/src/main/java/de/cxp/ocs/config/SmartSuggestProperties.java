package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
public class SmartSuggestProperties {

	@Setter
	@NonNull
	private Integer updateRateInSeconds = 60;

	private final List<String> preloadTenants = new ArrayList<>();

}
