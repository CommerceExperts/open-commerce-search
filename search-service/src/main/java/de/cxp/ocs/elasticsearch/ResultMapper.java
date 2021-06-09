package de.cxp.ocs.elasticsearch;

import static com.google.common.base.Predicates.instanceOf;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;

import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortOrder;

import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import de.cxp.ocs.model.result.ResultHit;

public class ResultMapper {

	private ResultMapper() {}

	public static ResultHit mapSearchHit(SearchHit hit, Map<String, SortOrder> sortedFields) {
		SearchHits variantHits = hit.getInnerHits().get("variants");
		SearchHit variantHit = null;
		if (variantHits.getHits().length > 0) {
			variantHit = variantHits.getAt(0);
		}

		ResultHit resultHit = new ResultHit().setDocument(getResultDocument(hit, variantHit))
				.setIndex(hit.getIndex()).setMatchedQueries(hit.getMatchedQueries());

		addSortFieldPrefix(hit, resultHit, sortedFields);
		return resultHit;
	}

	/**
	 * If we sort by a numeric value (e.g. price) and there are several
	 * different
	 * values at a product for that given field (e.g. multiple prices from the
	 * variants), then add a prefix "from" or "to" depending on sort order.
	 * 
	 * The goal is to show "from 10€" if sorted by price ascending and "to 59€"
	 * if
	 * sorted by price descending.
	 * 
	 * @param hit
	 * @param resultHit
	 * @param sortedFields
	 */
	@SuppressWarnings("unchecked")
	private static void addSortFieldPrefix(SearchHit hit, ResultHit resultHit, Map<String, SortOrder> sortedFields) {
		Object sortData = hit.getSourceAsMap().get(FieldConstants.SORT_DATA);
		if (sortData != null && sortData instanceof Map && sortedFields.size() > 0) {
			sortedFields.forEach((fieldName, order) -> {
				resultHit.document.getData().computeIfPresent(fieldName, (fn, v) -> {
					Object fieldSortData = ((Map<String, Object>) sortData).get(fn);
					if (fieldSortData != null && fieldSortData instanceof Collection
							&& ((Collection<?>) fieldSortData).size() > 1) {
						resultHit.document.set(fn + "_prefix", SortOrder.ASC.equals(order) ? "{from}" : "{to}");
						// collection is already sorted asc/desc: first value is
						// the relevant one
						return ((Collection<?>) fieldSortData).iterator().next();
					}
					return v;
				});
			});
		}
	}

	private static Document getResultDocument(SearchHit hit, SearchHit variantHit) {
		Map<String, Object> resultFields = new HashMap<>();
		for (String sourceDataField : new String[] { FieldConstants.SEARCH_DATA, FieldConstants.RESULT_DATA }) {
			putDataIntoResult(hit.getSourceAsMap(), resultFields, sourceDataField);
			if (variantHit != null) {
				putDataIntoResult(variantHit.getSourceAsMap(), resultFields, sourceDataField);
			}
		}

		return new Document(hit.getId()).setData(resultFields);
	}

	@SuppressWarnings("unchecked")
	private static void putDataIntoResult(Map<String, Object> source, Map<String, Object> resultFields, String sourceDataField) {
		Object sourceData = source.get(sourceDataField);
		if (sourceData != null && sourceData instanceof Map) {
			resultFields.putAll((Map<String, Object>) sourceData);
		}
	}

	@SuppressWarnings("unchecked")
	public static Document mapToOriginalDocument(String id, Map<String, Object> source, FieldConfigIndex fieldConfig) {
		Document mapped;

		Object variantsData = source.get(FieldConstants.VARIANTS);
		if (variantsData != null && isMapArray(variantsData, instanceOf(String.class), instanceOf(Object.class))) {
			mapped = new Product(id);
			Map<String, Object>[] variantSources = (Map<String, Object>[]) variantsData;
			Document[] variants = new Document[variantSources.length];
			for (int i = 0; i < variantSources.length; i++) {
				variants[i] = mapToOriginalDocument(id + "_" + i, variantSources[i], fieldConfig);
			}
			((Product) mapped).setVariants(variants);
		}
		else {
			mapped = new Document(id);
		}

		putDataIntoResult(source, mapped.getData(), FieldConstants.RESULT_DATA);

		return mapped;
	}

	private static boolean isMapArray(Object data, Predicate<Object> keyPredicate, Predicate<Object> valuePredicate) {
		boolean isWantedMap = data.getClass().isArray()
				&& ((Object[]) data).length > 0
				&& ((Object[]) data)[0] instanceof Map
				&& ((Map<?, ?>[]) data)[0].size() > 0;
		if (isWantedMap && keyPredicate != null && valuePredicate != null) {
			Entry<?, ?> mapContent = ((Map<?, ?>[]) data)[0].entrySet().iterator().next();
			isWantedMap &= keyPredicate.test(mapContent.getKey()) && valuePredicate.test(mapContent.getValue());
		}
		return isWantedMap;
	}

}
