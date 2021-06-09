package de.cxp.ocs.elasticsearch.prodset;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.StaticProductSet;

public interface ProductSetResolver {

	boolean runAsync();

	StaticProductSet resolve(ProductSet set, int extraBuffer, Searcher searcher, SearchContext searchContext);

}
