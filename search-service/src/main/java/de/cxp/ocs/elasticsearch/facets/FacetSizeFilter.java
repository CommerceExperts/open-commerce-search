package de.cxp.ocs.elasticsearch.facets;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FacetType;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.HierarchialFacetEntry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FacetSizeFilter implements FacetFilter {

	@Override
	public boolean isVisibleFacet(Facet facet, FacetConfig config, FilterContext filterContext, int totalMatchCount) {
		if (!hasEnoughElements(facet, config.getMinValueCount())) {
			log.debug("removing facet '{}' because it has less elements {} than the configured minValueCount {}",
					config.getLabel(), facet.getEntries().size(), config.getMinValueCount());
			return false;
		}
		return true;
	}

	private boolean hasEnoughElements(Facet facet, int minCount) {
		if (FacetType.RANGE.name().equalsIgnoreCase(facet.getType()) && facet.getEntries().size() >= 1) {
			return true;
		}
		if (FacetType.HIERARCHICAL.name().equalsIgnoreCase(facet.getType())) {
			if (facet.getEntries().size() >= minCount)
				return true;

			int allChildCount = 0;
			for (FacetEntry e : facet.getEntries()) {
				if (e instanceof HierarchialFacetEntry) {
					allChildCount += countLeafChildEntries((HierarchialFacetEntry) e);
				} else {
					allChildCount++;
				}

				// break fast
				if (allChildCount >= minCount)
					break;
			}
			return allChildCount >= minCount;
		}
		return facet.getEntries().size() >= minCount;
	}

	private int countLeafChildEntries(HierarchialFacetEntry parentEntry) {
		if (parentEntry.children.size() == 0)
			return 1;
		else {
			int leafChildCount = 0;
			for (FacetEntry child : parentEntry.getChildren()) {
				if (child instanceof HierarchialFacetEntry) {
					leafChildCount += countLeafChildEntries((HierarchialFacetEntry) child);
				}
			}
			return leafChildCount;
		}
	}
}
