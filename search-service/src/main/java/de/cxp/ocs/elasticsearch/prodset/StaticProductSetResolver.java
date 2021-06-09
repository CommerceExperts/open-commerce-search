package de.cxp.ocs.elasticsearch.prodset;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.StaticProductSet;

public class StaticProductSetResolver implements ProductSetResolver {

	@Override
	public StaticProductSet resolve(ProductSet productSet, int extraBuffer, Searcher searcher, SearchContext searchContext) {
		// TODO: verify if IDs actually exist (also async)
		return (StaticProductSet) productSet;
	}

	@Override
	public boolean runAsync() {
		return false;
	}

}
