package de.cxp.ocs.elasticsearch.prodset;

import java.util.Set;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.StaticProductSet;

public interface ProductSetResolver {

	/**
	 * <strong>
	 * This has no effect any more!
	 * </strong>
	 * 
	 * <p>
	 * Resolvers that need a bit more time can defined itself as async, so that in case of several product sets in a
	 * single request, all of them could run async in parallel.
	 * </p>
	 * 
	 * @return true if this product set resolver needs more time and should run async
	 */
	@Deprecated
	default boolean runAsync() {
		return false;
	}

	StaticProductSet resolve(ProductSet set, Set<String> excludedIds, Searcher searcher, SearchContext searchContext);

}
