package de.cxp.ocs.elasticsearch.facets;

import static de.cxp.ocs.elasticsearch.facets.FacetFactory.getLabel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.elasticsearch.search.aggregations.Aggregations;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.SearchConfiguration;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.util.SearchQueryBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FacetConfigurationApplyer {

	private final Map<String, FacetConfig>	facetsBySourceField	= new HashMap<>();
	private int								maxFacets;

	public FacetConfigurationApplyer(SearchConfiguration config) {
		maxFacets = config.getFacetConfiguration().getMaxFacets();
		for (FacetConfig facetConfig : config.getFacetConfiguration().getFacets()) {
			Optional<Field> sourceField = config.getIndexedFieldConfig().getField(facetConfig.getSourceField());

			if (sourceField.isPresent()) {
				if (facetsBySourceField.put(facetConfig.getSourceField(), facetConfig) != null) {
					log.warn("multiple facets based on same source field are not supported!"
							+ " Overwriting facet config for source field {}",
							facetConfig.getSourceField());
				}
			}
		}
	}

	public List<Facet> getFacets(Aggregations aggregations, List<FacetCreator> facetCreators, long matchCount,
			FilterContext filterContext, SearchQueryBuilder linkBuilder) {
		List<Facet> facets = new ArrayList<>();
		int actualMaxFacets = maxFacets;
		Set<String> duplicateFacets = new HashSet<>();

		// get filtered facets
		Set<String> appliedFilters = filterContext.getInternalFilters().keySet();

		for (FacetCreator fc : facetCreators) {
			Collection<Facet> createdFacets = fc.createFacets(aggregations, filterContext, linkBuilder);
			for (Facet f : createdFacets) {
				// skip facets with the identical name
				if (!duplicateFacets.add(getLabel(f))) {
					log.warn("duplicate facet with label " + getLabel(f));
					continue;
				}
				if (appliedFilters.contains(f.getFieldName())) {
					f.setFiltered(true);
				}

				FacetConfig facetConfig = facetsBySourceField.get(f.getFieldName());
				if (facetConfig != null) {
					if (facetConfig.isExcludeFromFacetLimit()) actualMaxFacets++;
				}
				facets.add(f);
			}
		}

		// sort by order and facet coverage
		facets.sort(new Comparator<Facet>() {

			@Override
			public int compare(Facet o1, Facet o2) {
				// prio 1: configured order
				int compare = Byte.compare(FacetFactory.getOrder(o1), FacetFactory.getOrder(o2));
				// prio 2: prefer facets with filtered value
				if (compare == 0) {
					compare = Boolean.compare(o2.isFiltered(), o1.isFiltered());
				}
				// prio 3: prefer higher facet coverage (reverse natural order)
				if (compare == 0) {
					compare = Long.compare(o2.getAbsoluteFacetCoverage(), o1.getAbsoluteFacetCoverage());
				}
				return compare;
			}

		});

		return facets.size() > actualMaxFacets ? facets.subList(0, actualMaxFacets) : facets;
	}
}
