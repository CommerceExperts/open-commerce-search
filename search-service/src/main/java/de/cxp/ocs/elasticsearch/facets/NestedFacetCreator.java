package de.cxp.ocs.elasticsearch.facets;

public interface NestedFacetCreator extends FacetCreator {

	NestedFacetCreator setNestedFacetCorrector(NestedFacetCountCorrector c);
}
