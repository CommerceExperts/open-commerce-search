package de.cxp.ocs.smartsuggest.spi;

/**
 * Implementations of this SPI are capable of providing source data for the suggest index.
 */
public interface SuggestDataProvider extends AbstractDataProvider<SuggestData> {

	/**
	 * In case the same data provider implementation more than once for an index
	 * to provide different kind of data, each instance should return a different
	 * name, so that the data sources can be archived and recovered separately.
	 * Per default the class-name is returned.
	 *
	 * @return name to distinguish data source
	 */
	default String getName() {
		return this.getClass().getSimpleName();
	}

}
