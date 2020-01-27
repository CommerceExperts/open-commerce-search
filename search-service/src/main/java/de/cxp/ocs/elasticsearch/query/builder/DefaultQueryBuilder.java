package de.cxp.ocs.elasticsearch.query.builder;

import java.util.List;

import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;

import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.util.ESQueryUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * a broad query that tries to get good results without loosing too much
 * precision.
 */
@RequiredArgsConstructor
public class DefaultQueryBuilder implements ESQueryBuilder {

	@Getter
	@Setter
	private String name;

	@Override
	public MasterVariantQuery buildQuery(List<QueryStringTerm> searchTerms) {
		QueryStringQueryBuilder mainQuery = QueryBuilders
				.queryStringQuery(ESQueryUtils.buildQueryString(searchTerms, Operator.AND.name()))
				.defaultField(FieldConstants.SEARCH_DATA + ".*")
				.analyzer("split")
				.fuzziness(Fuzziness.AUTO)
				.minimumShouldMatch("2<80%")
				.tieBreaker(0.8f);
		// WTF: for some reason the type() method is not fluent! ^^
		mainQuery.type(searchTerms.size() == 1 ? Type.BEST_FIELDS : Type.CROSS_FIELDS);
		mainQuery.queryName(name == null ? "defaultQuery" : name);

		QueryStringQueryBuilder variantQuery = QueryBuilders.queryStringQuery(ESQueryUtils.buildQueryString(searchTerms, " "))
				.minimumShouldMatch("1")
				.analyzer("split");
		variantQuery.type(Type.MOST_FIELDS);

		// isWithSpellCorrect=true because we use fuzzy matching
		return new MasterVariantQuery(mainQuery, variantQuery, true, false);
	}

	@Override
	public boolean allowParallelSpellcheckExecution() {
		return true;
	}
}
