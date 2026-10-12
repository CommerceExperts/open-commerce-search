package de.cxp.ocs.elasticsearch.facets;

import java.util.*;
import java.util.function.Function;

import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;

import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.util.DefaultLinkBuilder;
import lombok.AccessLevel;
import lombok.Getter;

public class VariantFacetCreator implements FacetCreator {

	@Getter(value = AccessLevel.PACKAGE)
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
	public AggregationBuilder buildAggregation(FilterContext filterContext) {
		return _buildAggregation(filterContext, creator -> creator.buildAggregation(filterContext));
	}

	public AggregationBuilder buildIncludeFilteredAggregation(FilterContext filterContext, Set<String> includeNames) {
		return _buildAggregation(filterContext, creator -> creator.buildIncludeFilteredAggregation(filterContext, includeNames));
	}

	public AggregationBuilder buildExcludeFilteredAggregation(FilterContext filterContext, Set<String> excludeNames) {
		return _buildAggregation(filterContext, creator -> creator.buildExcludeFilteredAggregation(filterContext, excludeNames));
	}

	public AggregationBuilder _buildAggregation(FilterContext filterContext, Function<FacetCreator, AggregationBuilder> subAggCreatorCall) {
		if (innerCreators.isEmpty()) return null;

		NestedAggregationBuilder variantNestedAggBuilder = AggregationBuilders.nested("_variants", FieldConstants.VARIANTS);

		AggregationBuilder finalNestedAggBuilder;
		if (filterContext.getVariantInnerHitFilter() != null) {
			FilterAggregationBuilder variantsFiltered = AggregationBuilders.filter("_variants_filtered", filterContext.getVariantInnerHitFilter());
			variantNestedAggBuilder.subAggregation(variantsFiltered);
			finalNestedAggBuilder = variantsFiltered;
		} else {
			finalNestedAggBuilder = variantNestedAggBuilder;
		}
		innerCreators.forEach(creator -> {
			finalNestedAggBuilder.subAggregation(subAggCreatorCall.apply(creator));
		});
		return variantNestedAggBuilder;
	}

	@Override
	public Collection<Facet> createFacets(Aggregations aggResult, FilterContext filterContext, DefaultLinkBuilder linkBuilder) {
		List<Facet> facets = new ArrayList<>();
		Nested nestedAgg = aggResult.get("_variants");
		Aggregations variantAggregations = nestedAgg.getAggregations();
		Aggregation filteredVariantAgg = variantAggregations.get("_variants_filtered");
		if (filteredVariantAgg != null) variantAggregations = ((ParsedFilter) filteredVariantAgg).getAggregations();

		for (FacetCreator creator : innerCreators) {
			facets.addAll(creator.createFacets(variantAggregations, filterContext, linkBuilder));
		}
		return facets;
	}

	@Override
	public Optional<Facet> mergeFacets(Facet first, Facet second) {
		Optional<Facet> mergedFacet = Optional.empty();
		for (FacetCreator creator : innerCreators) {
			mergedFacet = creator.mergeFacets(first, second);
			if (mergedFacet.isPresent()) {
				break;
			}
		}
		return mergedFacet;
	}

}
