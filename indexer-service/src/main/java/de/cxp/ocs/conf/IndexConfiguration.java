package de.cxp.ocs.conf;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

import de.cxp.ocs.config.DataProcessorConfiguration;
import de.cxp.ocs.config.FieldConfiguration;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
public class IndexConfiguration {

	@Setter
	@NonNull
	@NestedConfigurationProperty
	private DataProcessorConfiguration dataProcessorConfiguration = new DataProcessorConfiguration();

	@Setter
	@NonNull
	@NestedConfigurationProperty
	private FieldConfiguration fieldConfiguration = new FieldConfiguration();

}
