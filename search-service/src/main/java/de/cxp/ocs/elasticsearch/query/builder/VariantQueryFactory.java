package de.cxp.ocs.elasticsearch.query.builder;

import java.util.HashMap;
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
import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Accessors(chain = true)
public class VariantQueryFactory {

	@NonNull
	private final Map<String, Float> variantSearchFields;

	@NonNull
	private final FieldConfigAccess fieldConfig;

	private String analyzer = "standard";

	private String defaultSearchField = FieldConstants.VARIANTS + "." + FieldConstants.SEARCH_DATA + ".*";

	private Type type = Type.BEST_FIELDS;

	private float tieBreaker = 0.2f;

	public VariantQueryFactory(Map<String, Float> fieldWeights, FieldConfigAccess fieldConfig) {
		this.fieldConfig = fieldConfig;
		variantSearchFields = extractVariantSearchFields(fieldWeights);
	}

	private Map<String, Float> extractVariantSearchFields(Map<String, Float> weightedFields) {
		Map<String, Float> variantSearchFields = new HashMap<>();
		for (Entry<String, Float> fieldWeight : weightedFields.entrySet()) {
			String pureFieldName = fieldWeight.getKey();
			int subFieldDelimiterIndex = pureFieldName.indexOf('.');
			if (subFieldDelimiterIndex > 0) {
				pureFieldName = pureFieldName.substring(0, subFieldDelimiterIndex);
			}

			if (fieldConfig.getField(pureFieldName).map(Field::isVariantLevel).orElse(false)) {
				// don't use pureFieldName here, since we want to use the subField here
				variantSearchFields.put(FieldConstants.VARIANTS + "." + FieldConstants.SEARCH_DATA + "." + fieldWeight.getKey(), fieldWeight.getValue());
			}
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
