package de.cxp.ocs.conf.converter;

import de.cxp.ocs.config.Field;

/**
 * ConfigureableDataprocessor implementations manipulate, extract,
 * enrich, ... fields. As the configuration of that implementations has a huge
 * diversity, the configuration is done via YAML through the
 * DataProcessorConfiguration. To enable more type save programming than
 * working on a Map, the ConfigureableDataprocessor parses that
 * map into ConfigureableField implementations for specifying
 * ConfigureableDataprocessor implementations.
 */
public interface ConfigureableField {

	/**
	 * Gets the name of the field the ConfigureableDataprocessor is
	 * working on. This field is used the extract the record value before
	 * passing it to further processing steps.
	 * 
	 * @return the name of the {@link Field}.
	 */
	String getFieldName();

	// TODO: Let the ConfigureableField implementations do the mapping from
	// confMap to concrete class? static method does not work on interfaces,
	// could some builder or factory pattern be used
}
