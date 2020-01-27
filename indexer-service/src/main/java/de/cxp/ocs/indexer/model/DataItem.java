package de.cxp.ocs.indexer.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldUsage;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Base class which holds common data structure of either simple first level
 * items or for master and variant items.
 */
@AllArgsConstructor
@Data
public abstract class DataItem {

	/**
	 * single fields that should be part of the result response
	 */
	private final Map<String, Object> resultData = new HashMap<>();

	/**
	 * single fields that should be analyzed for search
	 */
	private final Map<String, Object> searchData = new HashMap<>();

	/**
	 * facet entries that will be used for standard text facets.
	 */
	private final List<FacetEntry<String>> termFacetData = new ArrayList<>();

	/**
	 * facet entries that will be used for numeric facets.
	 */
	private final List<FacetEntry<Number>> numberFacetData = new ArrayList<>();

	/**
	 * A map of scores that apply to that record at search time.
	 */
	private final Map<String, Object> scores = new HashMap<>();

	/**
	 * Fields that should be sortable. Values of that map should be "simple"
	 * values (string or number). Arrays or objects may cause undefined
	 * behavior.
	 */
	private final Map<String, Object> sortData = new HashMap<>();

	public void setValue(final Field field, final Object value) {
		for (final FieldUsage fu : field.getUsage()) {
			FieldUsageApplier.apply(fu, this, field, value);
		}

	}
}
