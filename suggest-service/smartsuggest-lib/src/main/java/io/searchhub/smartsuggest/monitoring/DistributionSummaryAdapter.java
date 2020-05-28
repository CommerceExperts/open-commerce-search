package io.searchhub.smartsuggest.monitoring;

import io.micrometer.core.instrument.DistributionSummary;

public interface DistributionSummaryAdapter {

	void record(double size);

	static DistributionSummaryAdapter of(DistributionSummary summary) {
		return summary::record;
	}
}
