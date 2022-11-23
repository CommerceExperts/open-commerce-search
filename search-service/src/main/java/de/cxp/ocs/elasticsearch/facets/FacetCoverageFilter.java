package de.cxp.ocs.elasticsearch.facets;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.model.result.Facet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FacetCoverageFilter implements FacetFilter {

	@Override
	public boolean isVisibleFacet(Facet facet, FacetConfig config, FilterContext filterContext, int totalMatchCount) {
		double facetCoverage = (double) facet.absoluteFacetCoverage / totalMatchCount;
		if (facetCoverage < config.getMinFacetCoverage()) {
			log.debug("removing facet '{}' because facet coverage of {} is lower than minFacetCoverage {}",
					config.getLabel(), facetCoverage, config.getMinFacetCoverage());
			return false;
		}
		return true;
	}

}
