package de.cxp.ocs.elasticsearch.facets;

import java.util.List;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.HierarchialFacetEntry;
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
		if (!facet.isFiltered && config.isRemoveOnSingleFullCoverageFacetElement() && isSingleFullCoverageFacet(facet, totalMatchCount)) {
			return false;
		}
		return true;
	}

	private boolean isSingleFullCoverageFacet(Facet facet, int totalMatchCount) {
		if (facet.entries.size() > 1 || facet.absoluteFacetCoverage < totalMatchCount) return false;
		FacetEntry facetEntry = facet.entries.get(0);
		switch (facetEntry.type) {
			case "term":
				return true;
			case "hierarchical":
				return isSingleNestedFacetEntry(facetEntry);
			default:
				return false;
		}
	}

	private boolean isSingleNestedFacetEntry(FacetEntry entry) {
		if (entry instanceof HierarchialFacetEntry) {
			List<FacetEntry> children = ((HierarchialFacetEntry) entry).getChildren();
			return children.isEmpty() || children.size() == 1 && isSingleNestedFacetEntry(children.get(0));
		}
		else {
			return true;
		}

	}

}
