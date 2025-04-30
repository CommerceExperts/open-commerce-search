package de.cxp.ocs.smartsuggest.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.NonNull;

public interface MeterRegistryAdapter {

	@NonNull
	MeterRegistry getMetricsRegistry();

	static MeterRegistryAdapter of(MeterRegistry registry) {
		return () -> registry;
	}
}
