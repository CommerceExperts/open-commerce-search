package de.cxp.ocs.elasticsearch.prodset;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.StaticProductSet;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ProductSetResolverUtil {

	@NonNull
	private final Map<String, ProductSetResolver> resolvers;
	
	public StaticProductSet[] resolve(ProductSet[] productSets, Searcher searcher, SearchContext searchContext) {
		StaticProductSet[] resolvedSets = new StaticProductSet[productSets.length];
		@SuppressWarnings("unchecked")
		CompletableFuture<Void>[] futures = new CompletableFuture[productSets.length];
		int nextPos = 0;
		// fetch extra products for dynamic product sets, in case there are
		// overlapping IDs
		final int[] extraBuffer = new int[] { 0 };
		for (ProductSet set : productSets) {
			int position = nextPos++;

			final ProductSetResolver resolver = resolvers.get(set.getType());

			if (resolver == null) {
				log.error("No resolver found for product set type '{}'", set.getType());
				resolvedSets[position] = new StaticProductSet().setIds(new String[0]).setName(set.getName());
				futures[position] = CompletableFuture.completedFuture(null);
			}
			// only run async, if there are more than 1 sets
			else if (resolver.runAsync() && productSets.length > nextPos) {
				futures[position] = CompletableFuture.supplyAsync(() -> resolver.resolve(set, extraBuffer[0], searcher, searchContext))
						.thenAccept(resolvedIds -> resolvedSets[position] = resolvedIds);
				extraBuffer[0] += set.getSize();
			}
			else {
				resolvedSets[position] = resolver.resolve(set, extraBuffer[0], searcher, searchContext);
				extraBuffer[0] += set.getSize();
				futures[position] = CompletableFuture.completedFuture(null);
			}
		}
		try {
			CompletableFuture.allOf(futures).join();

			if (productSets.length > 1) {
				// deduplicate ids from the different sets
				deduplicate(productSets, resolvedSets);
			}
		}
		catch (Exception e) {
			log.error("resolving product sets failed", e);
		}

		return resolvedSets;
	}

	private void deduplicate(ProductSet[] productSets, StaticProductSet[] resolvedSets) {
		Set<String> foundHeroProductIds = new HashSet<>();
		for (int i = 0; i < productSets.length; i++) {
			StaticProductSet resolvedSet = resolvedSets[i];
			if (i > 0) {
				List<String> deduplicatedIDs = new ArrayList<>(Math.min(productSets[i].getSize(), resolvedSet.ids.length));
				int k_offset = 0;
				for (int k = 0; k < productSets[i].getSize() && k + k_offset < resolvedSet.ids.length; k++) {
					while (foundHeroProductIds.add(resolvedSet.ids[k + k_offset]) == false && k + k_offset < resolvedSet.ids.length) {
						k_offset++;
					}
					deduplicatedIDs.add(resolvedSet.ids[k + k_offset]);
				}
				if (k_offset > 0 || deduplicatedIDs.size() != resolvedSet.ids.length) {
					resolvedSet.setIds(deduplicatedIDs.toArray(new String[deduplicatedIDs.size()]));
				}
			}
			else {
				for (String id : resolvedSet.ids) {
					foundHeroProductIds.add(id);
				}
			}
		}
	}

}
