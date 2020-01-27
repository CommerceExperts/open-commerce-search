package de.cxp.ocs.conf;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

import de.cxp.ocs.config.FieldConfiguration;
import lombok.Getter;

@Getter
public class IndexConfiguration {

	@NestedConfigurationProperty
	private final DataProcessorConfiguration dataProcessorConfiguration = new DataProcessorConfiguration();

	@NestedConfigurationProperty
	private final FieldConfiguration fieldConfiguration = new FieldConfiguration();

}
