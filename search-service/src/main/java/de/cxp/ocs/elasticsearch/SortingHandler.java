package de.cxp.ocs.elasticsearch;

import static de.cxp.ocs.util.DefaultLinkBuilder.sortStringRepresentation;

import java.util.*;
import java.util.stream.Collectors;

import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import de.cxp.ocs.config.*;
import de.cxp.ocs.model.result.Sorting;
import de.cxp.ocs.util.InternalSearchParams;
import de.cxp.ocs.util.DefaultLinkBuilder;
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
	 * @param parameters
	 * @param searchSourceBuilder
	 * @return a list of potential variant sorts
	 */
	void applySorting(List<Sorting> sortings, SearchSourceBuilder searchSourceBuilder) {
		boolean addedSorting = false;
		for (Sorting sorting : sortings) {
			SortOptionConfiguration sortConf = sortConfigIndex.get(sortStringRepresentation(sorting.field, sorting.sortOrder));
			Field sortField = sortFields.get(sorting.field);
			if (sortField != null) {
				String missingParam = sortConf != null ? sortConf.getMissing() : null;

				searchSourceBuilder.sort(
						SortBuilders.fieldSort(FieldConstants.SORT_DATA + "." + sorting.field)
								.order(sorting.sortOrder == null ? SortOrder.ASC : SortOrder.fromString(sorting.sortOrder.name()))
								.missing(missingParam));
				addedSorting = true;
			}
			else {
				log.debug("tried to sort by an unsortable field {}", sorting.field);
			}
		}

		// at the last stage, always sort by score
		if (addedSorting) {
			searchSourceBuilder.sort(SortBuilders.scoreSort());
		}
	}

	/**
	 * Extract variant sort definitions and return them as list.
	 * 
	 * @param parameters
	 * @param searchSourceBuilder
	 * @return a list of potential variant sorts
	 */
	List<SortBuilder<?>> getVariantSortings(List<Sorting> sortings) {
		List<SortBuilder<?>> variantSortings = sortings.isEmpty() ? Collections.emptyList() : new ArrayList<>(sortings.size());
		for (Sorting sorting : sortings) {
			SortOptionConfiguration sortConf = sortConfigIndex.get(sortStringRepresentation(sorting.field, sorting.sortOrder));
			Field sortField = sortFields.get(sorting.field);
			if (sortField != null && sortField.isVariantLevel()) {
				String missingParam = sortConf != null ? sortConf.getMissing() : null;
				variantSortings.add(
						SortBuilders
								.fieldSort(FieldConstants.VARIANTS + "." + FieldConstants.SORT_DATA + "." + sorting.field)
								.order(sorting.sortOrder == null ? SortOrder.ASC : SortOrder.fromString(sorting.sortOrder.name()))
								.missing(missingParam));
			}
		}
		return variantSortings;
	}

	Map<String, SortOrder> getSortedNumericFields(InternalSearchParams parameters) {
		Map<String, SortOrder> sortedNumberFields = new HashMap<>();
		for (Sorting sorting : parameters.sortings) {
			Field sortingField = sortFields.get(sorting.field);
			if (sortingField != null && FieldType.NUMBER.equals(sortingField.getType())) {
				sortedNumberFields.put(sorting.field, SortOrder.fromString(sorting.sortOrder.name()));
			}
		}
		return sortedNumberFields;
	}
}
