package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class DataProcessorConfiguration {

	private final List<String>						processors		= new ArrayList<>();
	private final Map<String, Map<String, String>>	configuration	= new LinkedHashMap<>();
}
