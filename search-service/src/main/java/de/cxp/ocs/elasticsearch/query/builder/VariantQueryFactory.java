package de.cxp.ocs.elasticsearch.query.builder;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;

import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.model.visitor.AbstractTermVisitor;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Accessors(chain = true)
public class VariantQueryFactory {

	@NonNull
	private final Map<String, Float> variantSearchFields;

	private String analyzer = "standard";

	private String defaultSearchField = FieldConstants.VARIANTS + "." + FieldConstants.SEARCH_DATA + ".*";

	private Type type = Type.BEST_FIELDS;

	private float tieBreaker = 0.2f;

	public VariantQueryFactory(Map<String, Float> fieldWeights) {
		variantSearchFields = ensureVariantPrefix(fieldWeights);
	}

	private Map<String, Float> ensureVariantPrefix(Map<String, Float> weightedFields) {
		Map<String, Float> variantSearchFields = new HashMap<>();
		String requiredPrefix = FieldConstants.VARIANTS + ".";
		for (Entry<String, Float> fieldWeight : weightedFields.entrySet()) {
			String fieldName = fieldWeight.getKey();
			if (!fieldName.startsWith(requiredPrefix)) {
				fieldName = requiredPrefix + fieldName;
			}
			variantSearchFields.put(FieldConstants.VARIANTS + "." + fieldWeight.getKey(), fieldWeight.getValue());
		}
		return variantSearchFields;
	}

	public QueryBuilder createMatchAnyTermQuery(ExtendedQuery searchTerms) {
		BoolQueryBuilder variantQuery = QueryBuilders.boolQuery();
		searchTerms.getSearchQuery().accept(
				AbstractTermVisitor.forEachTerm(
						term -> variantQuery.should(createTermQuery(term))));

		return variantQuery;
	}

	private QueryBuilder createTermQuery(QueryStringTerm term) {
		QueryStringQueryBuilder queryBuilder = QueryBuilders.queryStringQuery(term.toQueryString())
				.analyzer(analyzer)
				.type(type)
				.tieBreaker(tieBreaker)
				.boost(1f);
		if (variantSearchFields == null || variantSearchFields.isEmpty()) {
			queryBuilder.defaultField(defaultSearchField);
		}
		else {
			queryBuilder.fields(variantSearchFields);
		}
		return queryBuilder;
	}

}
