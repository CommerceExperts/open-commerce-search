package de.cxp.ocs.elasticsearch.facets;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;

import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.HierarchialFacetEntry;
import de.cxp.ocs.util.InternalSearchParams;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class CategoryFacetCreator implements FacetCreator {

	private static final String AGGREGATION_NAME = "_category";

	@Override
	public AbstractAggregationBuilder<?> buildAggregation(InternalSearchParams parameters) {
		return AggregationBuilders.terms(AGGREGATION_NAME)
				.field(FieldConstants.CATEGORY_FACET_DATA)
				.size(120)
				.minDocCount(1);
	}

	@Override
	public Collection<Facet> createFacets(List<InternalResultFilter> filters, Aggregations aggResult) {
		Terms categoryAgg = (Terms) aggResult.get(AGGREGATION_NAME);
		List<? extends Bucket> catBuckets = categoryAgg.getBuckets();
		if (catBuckets.size() == 0) return Collections.emptyList();

		Facet facet = new Facet(FieldConstants.CATEGORY_FACET_DATA);
		facet.setType(FieldType.category.name());

		Map<String, HierarchialFacetEntry> entries = new LinkedHashMap<>(catBuckets.size());
		long absDocCount = 0;

		for (Bucket categoryBucket : catBuckets) {
			String categoryPath = categoryBucket.getKeyAsString();

			String[] categories = StringUtils.split(categoryPath, '/');
			// TODO: in case a category is filtered, it might be a good idea to
			// only show the according path
			HierarchialFacetEntry lastLevelEntry = entries.computeIfAbsent(categories[0],
					c -> new HierarchialFacetEntry(c, 0));
			for (int i = 1; i < categories.length; i++) {
				FacetEntry child = lastLevelEntry.getChildByKey(categories[i]);
				if (child != null) {
					lastLevelEntry = (HierarchialFacetEntry) child;
				}
				else {
					HierarchialFacetEntry newChild = new HierarchialFacetEntry(categories[i], 0);
					lastLevelEntry.addChild(newChild);
					lastLevelEntry = newChild;
				}
			}
			absDocCount += categoryBucket.getDocCount();
			lastLevelEntry.setDocCount(categoryBucket.getDocCount());
			lastLevelEntry.setPath(categoryPath);
		}
		facet.setAbsoluteFacetCoverage(absDocCount);
		entries.values().forEach(facet.getEntries()::add);
		return Arrays.asList(facet);
	}

}
