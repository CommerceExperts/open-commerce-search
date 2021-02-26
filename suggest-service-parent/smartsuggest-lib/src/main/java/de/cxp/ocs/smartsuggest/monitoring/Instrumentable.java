package de.cxp.ocs.smartsuggest.monitoring;

import java.util.Optional;

public interface Instrumentable {

	void setMetricsRegistryAdapter(Optional<MeterRegistryAdapter> metricsRegistryAdapter);

}
