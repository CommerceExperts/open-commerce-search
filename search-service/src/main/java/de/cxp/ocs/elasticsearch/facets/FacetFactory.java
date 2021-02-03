package de.cxp.ocs.elasticsearch.facets;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FacetType;
import de.cxp.ocs.model.result.Facet;

public class FacetFactory {

	private static enum MetaDataValues {
		label, order, multiSelect
	}

	public static String getLabel(Facet facet) {
		return facet.meta.get(MetaDataValues.label.name()).toString();
	}

	public static byte getOrder(Facet facet) {
		return (byte) facet.meta.get(MetaDataValues.order.name());
	}

	public static Facet create(FacetConfig facetConfig, FacetType type) {
		Facet facet = new Facet(facetConfig.getSourceField());
		facet.getMeta().putAll(facetConfig.getMetaData());

		facet.setType(type.name());
		facet.meta.put(MetaDataValues.label.name(), facetConfig.getLabel());
		facet.meta.put(MetaDataValues.multiSelect.name(), facetConfig.isMultiSelect());
		facet.meta.put(MetaDataValues.order.name(), facetConfig.getOrder());
		return facet;
	}

}
