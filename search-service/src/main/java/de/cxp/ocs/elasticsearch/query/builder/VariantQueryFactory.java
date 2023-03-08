package de.cxp.ocs.elasticsearch.query.builder;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.visitor.AbstractTermVisitor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Accessors(chain = true)
public class VariantQueryFactory {

	private String analyzer = "standard";

	private String defaultSearchField = FieldConstants.VARIANTS + "." + FieldConstants.SEARCH_DATA + ".*";

	private Type type = Type.BEST_FIELDS;

	private float tieBreaker = 0.2f;

	public QueryBuilder createMatchAnyTermQuery(ExtendedQuery searchTerms) {
		BoolQueryBuilder variantQuery = QueryBuilders.boolQuery();
		searchTerms.getSearchQuery().accept(
				AbstractTermVisitor.forEachTerm(
						term -> variantQuery.should(
								QueryBuilders.queryStringQuery(term.toQueryString())
										.analyzer(analyzer)
										.defaultField(defaultSearchField)
										.type(type)
										.tieBreaker(tieBreaker)
										.boost(0.1f))));

		return variantQuery;
	}

}
