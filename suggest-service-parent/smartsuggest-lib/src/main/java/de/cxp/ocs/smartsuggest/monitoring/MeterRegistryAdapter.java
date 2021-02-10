package de.cxp.ocs.smartsuggest.monitoring;

import io.micrometer.core.instrument.MeterRegistry;

public interface MeterRegistryAdapter {

	MeterRegistry getMetricsRegistry();

	static MeterRegistryAdapter of(MeterRegistry registry) {
		return () -> registry;
	}
}
