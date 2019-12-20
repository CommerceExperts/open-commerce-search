package de.cxp.ocs.config;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
public class SearchConfiguration {

	@NonNull
	private String indexName;

	@NonNull
	private FieldConfiguration fieldConfiguration = new FieldConfiguration();

	@NonNull
	private FacetConfiguration facetConfiguration = new FacetConfiguration();

	@NonNull
	private ScoringConfiguration scoring = new ScoringConfiguration();

	@NonNull
	private final Map<String, QueryConfiguration> queryConfigs = new LinkedHashMap<>();


}
