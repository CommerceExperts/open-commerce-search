package de.cxp.ocs.elasticsearch.query;

import org.elasticsearch.index.query.QueryBuilder;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MasterVariantQuery {

	private QueryBuilder masterLevelQuery;

	private QueryBuilder variantLevelQuery;

	private boolean isWithSpellCorrection = false;

	private boolean acceptNoResult = false;
}
