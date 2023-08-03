package de.cxp.ocs.elasticsearch.prodset;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class HeroProductsQuery {

	private final QueryBuilder mainQuery;

	private final QueryBuilder variantBoostQuery;

	public QueryBuilder applyToMasterQuery(QueryBuilder masterMatchQuery) {
		return _apply(masterMatchQuery, mainQuery);
	}

	public QueryBuilder applyToVariantQuery(QueryBuilder variantsMatchQuery) {
		return _apply(variantsMatchQuery, variantBoostQuery);
	}

	private QueryBuilder _apply(QueryBuilder originalQuery, QueryBuilder heroQuery) {
		if (heroQuery == null) {
			return originalQuery;
		}
		if (originalQuery == null) {
			return heroQuery;
		}
		return QueryBuilders.disMaxQuery()
				.add(heroQuery)
				.add(originalQuery)
				.tieBreaker(0f);
	}

}
