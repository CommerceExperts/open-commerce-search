package de.cxp.ocs.smartsuggest.spi;

/**
 * Data that's actuality can be assigned to a date and time.
 */
public interface DatedData {

	/**
	 * Get unix timestamp in millis for the last time this data was modified.
	 *
	 * @return unix time in millis
	 */
	long getModificationTime();
}
