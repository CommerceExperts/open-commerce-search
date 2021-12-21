package de.cxp.ocs.elasticsearch.mapper;

import java.util.function.BiFunction;

import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import lombok.RequiredArgsConstructor;

/**
 * A variant-picking-strategy defines how variants are chosen to be result-hits
 * instead of their main product.
 */
// If customizations are required, define an interface that declares the
// required methods and let this enum implement it.
@RequiredArgsConstructor
public enum VariantPickingStrategy implements BiFunction<SearchHits, Integer, SearchHit> {


	/**
	 * pick first variant if available.
	 */
	pickAlways(false) {

		@Override
		public SearchHit pick(SearchHits variantHits, Integer allVariantHitCount) {
			return variantHits.getHits().length > 0 ? variantHits.getAt(0) : null;
		}

	},

	/**
	 * Pick best variant if not all variants are part of the result anymore, so
	 * some variants were filtered.
	 */
	pickIfDrilledDown(true) {

		@Override
		public SearchHit pick(SearchHits variantHits, Integer allVariantHitCount) {
			return (int) variantHits.getTotalHits().value < allVariantHitCount || variantHits.getHits().length == 1 ? variantHits.getAt(0) : null;
		}

	},

	/**
	 * Pick first variant, if it has a better score than the second one or if
	 * it's the only one left.
	 */
	pickIfBestScored(false) {

		@Override
		public SearchHit pick(SearchHits variantHits, Integer allVariantHitCount) {
			if (variantHits.getHits().length == 1
					|| (variantHits.getHits().length >= 2 && variantHits.getAt(0).getScore() > variantHits.getAt(1).getScore())) {
				return variantHits.getAt(0);
			}
			return null;
		}

	},

	/**
	 * Picks a variant only if there are no other variants matching.
	 */
	pickIfSingleHit(false) {

		@Override
		public SearchHit pick(SearchHits variantHits, Integer allVariantHitCount) {
			return variantHits.getHits().length == 1 ? variantHits.getAt(0) : null;
		}

	};

	private final boolean requiresAllVariantHitCount;

	@Override
	public SearchHit apply(SearchHits variantHits, Integer allVariantHitCount) {
		return pick(variantHits, allVariantHitCount);
	}

	abstract SearchHit pick(SearchHits variantHits, Integer allVariantHitCount);

	public boolean isAllVariantHitCountRequired() {
		return requiresAllVariantHitCount;
	}

}
