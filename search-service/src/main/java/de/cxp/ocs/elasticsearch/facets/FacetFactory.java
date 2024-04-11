package de.cxp.ocs.elasticsearch.facets;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FacetType;
import de.cxp.ocs.model.result.Facet;

public class FacetFactory {

	private static enum MetaDataValues {
		label, order, multiSelect
	}

	public static String getLabel(Facet facet) {
		return facet.meta.getOrDefault(MetaDataValues.label.name(), facet.getFieldName()).toString();
	}

	public static int getOrder(Facet facet) {
		return (int) facet.meta.get(MetaDataValues.order.name());
	}

	public static Facet create(FacetConfig facetConfig, FacetType type) {
		return create(facetConfig, type.name().toLowerCase());
	}

	public static Facet create(FacetConfig facetConfig, String type) {
		Facet facet = new Facet(facetConfig.getSourceField());
		facet.getMeta().putAll(facetConfig.getMetaData());

		facet.setType(type);
		facet.meta.put(MetaDataValues.label.name(), facetConfig.getLabel() == null ? facetConfig.getSourceField() : facetConfig.getLabel());
		facet.meta.put(MetaDataValues.multiSelect.name(), facetConfig.isMultiSelect());
		facet.meta.put(MetaDataValues.order.name(), facetConfig.getOrder());
		return facet;
	}

}
