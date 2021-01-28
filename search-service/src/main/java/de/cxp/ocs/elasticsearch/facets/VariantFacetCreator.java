package de.cxp.ocs.elasticsearch.facets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;

import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.util.SearchQueryBuilder;

public class VariantFacetCreator implements FacetCreator {

	private final Collection<FacetCreator> innerCreators;

	public VariantFacetCreator(Collection<FacetCreator> creators) {
		innerCreators = creators;
		NestedFacetCountCorrector facetCorrector = new NestedFacetCountCorrector(FieldConstants.VARIANTS);
		creators.forEach(c -> {
			if (c instanceof NestedFacetCreator) {
				((NestedFacetCreator) c).setNestedFacetCorrector(facetCorrector);
			}
		});
	}

	@Override
	public AbstractAggregationBuilder<?> buildAggregation(FilterContext filters) {
		if (innerCreators.size() == 0) return null;
		NestedAggregationBuilder nestedAggBuilder = AggregationBuilders.nested("_variants", FieldConstants.VARIANTS);
		innerCreators.forEach(creator -> {
			nestedAggBuilder.subAggregation(creator.buildAggregation(filters));
		});
		return nestedAggBuilder;
	}

	@Override
	public Collection<Facet> createFacets(Aggregations aggResult, FilterContext filterContext, SearchQueryBuilder linkBuilder) {
		List<Facet> facets = new ArrayList<>();
		Nested nestedAgg = (Nested) aggResult.get("_variants");
		for (FacetCreator creator : innerCreators) {
			facets.addAll(creator.createFacets(nestedAgg.getAggregations(), filterContext, linkBuilder));
		}
		return facets;
	}

}
