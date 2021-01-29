package de.cxp.ocs.elasticsearch.facets;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.common.collect.ImmutableSet;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;

public class GenericFacetCreatorFactory implements FacetCreatorFactory {

	private final Function<Map<String, FacetConfig>, FacetCreator> factory;

	private final Set<String> supportedFacetTypes;

	public GenericFacetCreatorFactory(Function<Map<String, FacetConfig>, FacetCreator> innerFactory, String... allowedFacetTypes) {
		factory = innerFactory;
		supportedFacetTypes = ImmutableSet.copyOf(allowedFacetTypes);
	}

	@Override
	public boolean supportsType(String facetType) {
		return supportedFacetTypes.contains(facetType);
	}

	@Override
	public FacetCreator create(Map<String, FacetConfig> facetConfigs) {
		return factory.apply(facetConfigs);
	}

}
