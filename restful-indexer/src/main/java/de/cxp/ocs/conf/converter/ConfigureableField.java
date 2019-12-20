package de.cxp.ocs.conf.converter;

import java.util.Map;

import de.cxp.ocs.conf.DataProcessorConfiguration;
import de.cxp.ocs.preprocessor.ConfigureableDataprocessor;

/**
 * {@link ConfigureableDataprocessor} implementations manipulate, extract,
 * enrich, ... fields. As the configuration of that implementations has a huge
 * diversity, the configuration is done via YAML through the
 * {@link DataProcessorConfiguration}. To enable more type save programming than
 * working on a {@link Map}, the {@link ConfigureableDataprocessor} parses that
 * map into {@link ConfigureableField} implementations for specifig
 * {@link ConfigureableDataprocessor} implementations.
 */
public interface ConfigureableField {

	/**
	 * Gets the name of the field the {@link ConfigureableDataprocessor} is
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
