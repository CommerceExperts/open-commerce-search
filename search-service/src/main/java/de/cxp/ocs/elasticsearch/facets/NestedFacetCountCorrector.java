package de.cxp.ocs.elasticsearch.facets;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation.Bucket;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedReverseNested;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class NestedFacetCountCorrector {

	@NonNull
	private final String nestedPath;

	public String getNestedPathPrefix() {
		return nestedPath.isEmpty() ? nestedPath : nestedPath + ".";
	}

	public void correctValueAggBuilder(AggregationBuilder aggBuilder) {
		aggBuilder.subAggregation(AggregationBuilders.reverseNested("_reverse"));
	}

	public long getCorrectedDocumentCount(Bucket valueBucket) {
		return ((ParsedReverseNested) valueBucket.getAggregations().get("_reverse")).getDocCount();
	}

}
