package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Final search-configuration that contains all the fetched configuration
 * objects.
 */
@Data
@NoArgsConstructor
public class SearchConfiguration {

	@NonNull
	private String indexName;

	@NonNull
	private FieldConfigIndex indexedFieldConfig;

	@NonNull
	private FacetConfiguration facetConfiguration = new FacetConfiguration();

	@NonNull
	private ScoringConfiguration scoring = new ScoringConfiguration();

	@NonNull
	private final List<QueryConfiguration> queryConfigs = new ArrayList<>();

	@NonNull
	private final List<SortOptionConfiguration> sortConfigs = new ArrayList<>();
}
