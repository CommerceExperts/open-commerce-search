package de.cxp.ocs.elasticsearch.facets;

import de.cxp.ocs.config.FacetType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Data
@EqualsAndHashCode
@RequiredArgsConstructor
class FacetCreatorClassifier {

	final static FacetCreatorClassifier hierarchicalFacet = new FacetCreatorClassifier(false, FacetType.HIERARCHICAL.name());

	final static FacetCreatorClassifier	masterIntervalFacet	= new FacetCreatorClassifier(false, FacetType.INTERVAL.name());
	final static FacetCreatorClassifier	masterRangeFacet	= new FacetCreatorClassifier(false, FacetType.RANGE.name());
	final static FacetCreatorClassifier	masterTermFacet		= new FacetCreatorClassifier(false, FacetType.TERM.name());

	final static FacetCreatorClassifier	variantIntervalFacet	= new FacetCreatorClassifier(true, FacetType.INTERVAL.name());
	final static FacetCreatorClassifier	variantRangeFacet		= new FacetCreatorClassifier(true, FacetType.RANGE.name());
	final static FacetCreatorClassifier	variantTermFacet		= new FacetCreatorClassifier(true, FacetType.TERM.name());

	final boolean onVariantLevel;

	@NonNull
	// not using the FacetType enum here to support custom facet types
	private final String facetType;

	private final boolean isExplicitFacetCreator;

	/**
	 * Per default a facet creators is used for generic facet creation. Therefore this constructor is a classification
	 * for such creators.
	 * 
	 * @param isOnVariantLevel
	 * @param facetType
	 */
	public FacetCreatorClassifier(boolean isOnVariantLevel, String facetType) {
		this(isOnVariantLevel, facetType, false);
	}
}
