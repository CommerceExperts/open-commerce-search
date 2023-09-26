package de.cxp.ocs.elasticsearch.query.builder;

import java.util.Map;
import java.util.Map.Entry;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.model.visitor.AbstractTermVisitor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Accessors(chain = true)
@RequiredArgsConstructor
public class VariantQueryFactory {

	@NonNull
	private final FieldConfigAccess fieldConfig;

	private String analyzer = "standard";

	private String defaultSearchField = FieldConstants.VARIANTS + "." + FieldConstants.SEARCH_DATA + ".*";

	private Type type = Type.BEST_FIELDS;

	private float tieBreaker = 0.2f;

	public QueryBuilder createMatchAnyTermQuery(ExtendedQuery searchTerms, Map<String, Float> weightedFields) {
		BoolQueryBuilder variantQuery = QueryBuilders.boolQuery();
		searchTerms.getSearchQuery().accept(
				AbstractTermVisitor.forEachTerm(
						term -> variantQuery.should(createTermQuery(term, weightedFields))));

		return variantQuery;
	}

	private QueryBuilder createTermQuery(QueryStringTerm term, Map<String, Float> weightedFields) {
		QueryStringQueryBuilder queryBuilder = QueryBuilders.queryStringQuery(term.toQueryString())
				.analyzer(analyzer)
				.type(type)
				.tieBreaker(tieBreaker)
				.boost(1f);
		if (weightedFields == null || weightedFields.isEmpty()) {
			queryBuilder.defaultField(defaultSearchField);
		}
		else {
			for (Entry<String, Float> fieldWeight : weightedFields.entrySet()) {
				if (fieldConfig.getField(fieldWeight.getKey()).map(Field::isVariantLevel).orElse(false)) {
					queryBuilder.field(FieldConstants.VARIANTS + "." + FieldConstants.SEARCH_DATA + "." + fieldWeight.getKey(), fieldWeight.getValue());
				}
			}
		}
		return queryBuilder;
	}

}
