package de.cxp.ocs.elasticsearch;

import static de.cxp.ocs.config.FieldConstants.NUMBER_FACET_DATA;
import static de.cxp.ocs.config.FieldConstants.PATH_FACET_DATA;
import static de.cxp.ocs.config.FieldConstants.TERM_FACET_DATA;
import static de.cxp.ocs.util.DefaultLinkBuilder.sortStringRepresentation;

import java.util.*;
import java.util.stream.Collectors;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.NestedSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import de.cxp.ocs.config.*;
import de.cxp.ocs.elasticsearch.query.sort.SortInstruction;
import de.cxp.ocs.model.result.Sorting;
import de.cxp.ocs.util.DefaultLinkBuilder;
import de.cxp.ocs.util.InternalSearchParams;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SortingHandler {

	private final Map<String, Field>					sortFields;
	private final List<SortOptionConfiguration>			sortConfigs;
	private final Map<String, SortOptionConfiguration>	sortConfigIndex;

	public SortingHandler(@NonNull FieldConfigIndex fieldIndex, @NonNull List<SortOptionConfiguration> sortConfigs) {
		Map<String, Field> tempSortFields = fieldIndex.getFieldsByUsage(FieldUsage.SORT);
		sortFields = Collections.unmodifiableMap(tempSortFields);
		this.sortConfigs = sortConfigs;
		sortConfigIndex = sortConfigs.stream().collect(Collectors.toMap(s -> sortStringRepresentation(s.getField(), s.getOrder()), s -> s));
	}

	List<Sorting> buildSortOptions(DefaultLinkBuilder linkBuilder) {
		List<Sorting> sortings = new ArrayList<>();

		if (sortConfigs.isEmpty()) {
			// without sort configs,
			for (Field sortField : sortFields.values()) {
				for (de.cxp.ocs.model.result.SortOrder order : de.cxp.ocs.model.result.SortOrder.values()) {
					sortings.add(new Sorting(sortField.getName() + "." + order.toString(), sortField.getName(), order,
							linkBuilder.isSortingActive(sortField, order),
							linkBuilder.withSortingLink(sortField, order)));
				}
			}
		}
		else {
			for (SortOptionConfiguration sortConf : sortConfigs) {
				if (sortConf.getOrder() == null) continue;
				Field sortField = sortFields.get(sortConf.getField());
				if (sortField != null) {
					sortings.add(new Sorting(sortConf.getLabel(), sortConf.getField(), sortConf.getOrder(),
							linkBuilder.isSortingActive(sortField, sortConf.getOrder()),
							linkBuilder.withSortingLink(sortField, sortConf.getOrder())));
				}
			}
		}

		return sortings;
	}

	/**
	 * Applies sort definitions onto the searchSourceBuilder and if some of
	 * these
	 * sorts also apply to the variant level, it will create these sort
	 * definitions
	 * and return them as list.
	 * 
	 * @param sortings
	 * @param searchSourceBuilder
	 * @return a list of potential variant sorts
	 */
	void applySorting(List<SortInstruction> sortings, SearchSourceBuilder searchSourceBuilder) {
		boolean addedSorting = false;
		for (SortInstruction sorting : sortings) {
			SortOptionConfiguration sortConf = sortConfigIndex.get(sortStringRepresentation(sorting.getField().getName(), sorting.getSortOrder()));
			String missingParam = sortConf != null ? sortConf.getMissing() : null;
			Field sortField = sorting.getField();
			if (sortField == null) {
				log.debug("tried to sort by an unsortable field {}", sorting.getRawSortValue());
			}
			else if (sortField.hasUsage(FieldUsage.SORT)) {
				searchSourceBuilder.sort(
						SortBuilders.fieldSort(FieldConstants.SORT_DATA + "." + sortField.getName())
								.order(mapSortOrder(sorting.getSortOrder()))
								.missing(missingParam));
				addedSorting = true;
			}
			// it is possible to sort on a nested facet field including some filtering
			// example:
			// - "sort=brand" will simply sort by brand
			// - "sort=brand.Puma" will sort all Puma products to the top (or bottom depending on the order + missing
			// value)
			// - "sort=size.id.XL" will sort all products with id=XL by their according value (which might be a higher
			// grained value)
			else if (sortField.hasUsage(FieldUsage.FACET)) {
				String fieldPrefix = getFacetBasedSortFieldPrefix(sorting, sortField);
				if (fieldPrefix != null) {
					BoolQueryBuilder nestedSortFilter = getNestedSortFilter(sorting, sortField, fieldPrefix);
					searchSourceBuilder.sort(
							SortBuilders.fieldSort(fieldPrefix + ".value")
									.order(mapSortOrder(sorting.getSortOrder()))
									.missing(missingParam)
									.setNestedSort(new NestedSortBuilder(fieldPrefix).setFilter(nestedSortFilter)));
				}
			}
		}

		// at the last stage, always sort by score
		if (addedSorting) {
			searchSourceBuilder.sort(SortBuilders.scoreSort());
		}
	}

	private BoolQueryBuilder getNestedSortFilter(SortInstruction sorting, Field sortField, String fieldPrefix) {
		BoolQueryBuilder sortValueFilter = QueryBuilders.boolQuery()
				.filter(QueryBuilders.termQuery(fieldPrefix + ".name", sortField.getName()));

		int suffixIndex = sorting.getRawSortValue().indexOf('.');
		if (suffixIndex > 0) {
			String sortValue = sorting.getRawSortValue().substring(suffixIndex + 1);
			String sortFilterField = fieldPrefix;
			if (sortValue.startsWith("id.")) {
				sortFilterField += ".id";
				sortValue = sortValue.substring(3);
			}
			else {
				sortFilterField += ".value";
			}
			sortValueFilter.filter(QueryBuilders.termQuery(sortFilterField, sortValue));
		}
		return sortValueFilter;
	}

	private SortOrder mapSortOrder(de.cxp.ocs.model.result.SortOrder sortOrder) {
		return sortOrder == null ? SortOrder.ASC : SortOrder.fromString(sortOrder.name());
	}

	private String getFacetBasedSortFieldPrefix(SortInstruction sorting, Field sortField) {
		String fieldPrefix = null;
		switch (sortField.getType()) {
			case CATEGORY:
				fieldPrefix = PATH_FACET_DATA;
				break;
			case STRING:
				fieldPrefix = TERM_FACET_DATA;
				break;
			case NUMBER:
				fieldPrefix = NUMBER_FACET_DATA;
				break;
			default:
				log.debug("tried to sort by an unsortable field {}", sorting.getField());
		}
		return fieldPrefix;
	}

	/**
	 * Extract variant sort definitions and return them as list.
	 * 
	 * @param sortings
	 * @return a list of potential variant sorts
	 */
	List<SortBuilder<?>> getVariantSortings(List<SortInstruction> sortings) {
		List<SortBuilder<?>> variantSortings = sortings.isEmpty() ? Collections.emptyList() : new ArrayList<>(sortings.size());
		for (SortInstruction sorting : sortings) {
			SortOptionConfiguration sortConf = sortConfigIndex.get(sortStringRepresentation(sorting.getField().getName(), sorting.getSortOrder()));
			if (sorting.getField() != null && sorting.getField().isVariantLevel()) {
				String missingParam = sortConf != null ? sortConf.getMissing() : null;
				variantSortings.add(
						SortBuilders
								.fieldSort(FieldConstants.VARIANTS + "." + FieldConstants.SORT_DATA + "." + sorting.getField().getName())
								.order(mapSortOrder(sorting.getSortOrder()))
								.missing(missingParam));
			}
		}
		return variantSortings;
	}

	Map<String, SortOrder> getSortedNumericFields(InternalSearchParams parameters) {
		Map<String, SortOrder> sortedNumberFields = new HashMap<>();
		for (SortInstruction sorting : parameters.sortings) {
			Field sortingField = sorting.getField();
			if (sortingField != null && FieldType.NUMBER.equals(sortingField.getType())) {
				sortedNumberFields.put(sorting.getField().getName(), mapSortOrder(sorting.getSortOrder()));
			}
		}
		return sortedNumberFields;
	}
}
