package de.cxp.ocs.elasticsearch.query.builder;

import java.util.List;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Accessors(chain = true)
public class VariantQueryFactory {

	private String analyzer = "standard";

	private String defaultSearchField = FieldConstants.VARIANTS + "." + FieldConstants.SEARCH_DATA + ".*";

	private Type type = Type.BEST_FIELDS;

	private float tieBreaker = 0.2f;

	public QueryBuilder createMatchAnyTermQuery(List<QueryStringTerm> searchTerms) {
		BoolQueryBuilder variantQuery = QueryBuilders.boolQuery();
		for (QueryStringTerm term : searchTerms) {
			variantQuery.should(
					QueryBuilders.queryStringQuery(term.toQueryString())
							.analyzer(analyzer)
							.defaultField(defaultSearchField)
							.type(type)
							.tieBreaker(tieBreaker)
							.boost(0.1f));
		}
		return variantQuery;
	}

}
