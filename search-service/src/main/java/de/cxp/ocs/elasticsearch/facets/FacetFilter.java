package de.cxp.ocs.elasticsearch.facets;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.model.result.Facet;

public interface FacetFilter {

	boolean isVisibleFacet(Facet facet, FacetConfig config, FilterContext filterContext, int totalMatchCount);

}
