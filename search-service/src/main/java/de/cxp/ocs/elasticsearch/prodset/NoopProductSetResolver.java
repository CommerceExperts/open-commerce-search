package de.cxp.ocs.elasticsearch.prodset;

import java.util.Set;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.StaticProductSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoopProductSetResolver implements ProductSetResolver {

	@Override
	public boolean runAsync() {
		return false;
	}

	@Override
	public StaticProductSet resolve(ProductSet set, Set<String> excludedIds, Searcher searcher, SearchContext searchContext) {
		log.info("received productSet {} of type {} but no resolver defined for it!", set.getName(), set.getType());
		return new StaticProductSet(set.getType(), new String[0], set.getName());
	}

}
