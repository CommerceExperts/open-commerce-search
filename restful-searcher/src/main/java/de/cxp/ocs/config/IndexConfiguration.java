package de.cxp.ocs.config;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

import lombok.Getter;

@Getter
public class IndexConfiguration {

	@NestedConfigurationProperty
	private final FieldConfiguration fieldConfiguration = new FieldConfiguration();

}
