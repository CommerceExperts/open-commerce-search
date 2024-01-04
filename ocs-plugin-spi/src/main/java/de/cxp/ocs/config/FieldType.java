package de.cxp.ocs.config;

public enum FieldType {
	STRING, NUMBER, CATEGORY, 
	/**
	 * Raw type is just passed to Elasticsearch as is without any validation or transformation.
	 * If multiple source-fields are used for the same raw target field,
	 * merging is done as usual. However since no validation is done, the responsibility
	 * for proper data types is in the hand of the indexer-client. Handle with care.
	 * With great power comes great responsibility.
	 */
	RAW,
	@Deprecated ID, 
}
