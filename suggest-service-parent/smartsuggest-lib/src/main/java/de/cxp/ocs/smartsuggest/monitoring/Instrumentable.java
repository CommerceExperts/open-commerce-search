package de.cxp.ocs.smartsuggest.monitoring;

import io.micrometer.core.instrument.Tag;

public interface Instrumentable {

	/**
	 * Optional meter registry (adapter that gives access to the actual
	 * meter-registry). If not available, no metrics should be measured.
	 * 
	 * @param metricsRegistryAdapter
	 *        optional adapter
	 * @param tags
	 *        these "standard" tags should be used for all added sensors. More
	 *        tags can be added.
	 */
	void instrument(MeterRegistryAdapter metricsRegistryAdapter, Iterable<Tag> tags);

}
