package de.cxp.ocs.elasticsearch.facets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.elasticsearch.search.aggregations.AggregationBuilder;
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
		NestedFacetCountCorrector nestedFacetCountCorrector = new NestedFacetCountCorrector(FieldConstants.VARIANTS);
		creators.forEach(c -> {
			if (c instanceof NestedFacetCreator) {
				((NestedFacetCreator) c).setNestedFacetCorrector(nestedFacetCountCorrector);
			}
		});
	}

	@Override
	public AggregationBuilder buildAggregation() {
		return _buildAggregation(FacetCreator::buildAggregation);
	}

	public AggregationBuilder buildIncludeFilteredAggregation(Set<String> includeNames) {
		return _buildAggregation(creator -> creator.buildIncludeFilteredAggregation(includeNames));
	}

	public AggregationBuilder buildExcludeFilteredAggregation(Set<String> excludeNames) {
		return _buildAggregation(creator -> creator.buildExcludeFilteredAggregation(excludeNames));
	}

	public AggregationBuilder _buildAggregation(Function<FacetCreator, AggregationBuilder> subAggCreatorCall) {
		if (innerCreators.size() == 0) return null;
		NestedAggregationBuilder nestedAggBuilder = AggregationBuilders.nested("_variants", FieldConstants.VARIANTS);
		innerCreators.forEach(creator -> {
			nestedAggBuilder.subAggregation(subAggCreatorCall.apply(creator));
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
