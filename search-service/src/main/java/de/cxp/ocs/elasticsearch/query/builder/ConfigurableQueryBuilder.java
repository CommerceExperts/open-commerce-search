package de.cxp.ocs.elasticsearch.query.builder;

import static de.cxp.ocs.config.QueryBuildingSetting.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;

import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WeightedWord;
import de.cxp.ocs.util.ESQueryUtils;
import de.cxp.ocs.util.Util;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * creates a configurable query-string query
 */
@RequiredArgsConstructor
public class ConfigurableQueryBuilder implements ESQueryBuilder {

	private final Map<QueryBuildingSetting, String>	querySettings;
	private final Map<String, Float>				weightedFields;

	@Getter
	@Setter
	private String name;

	@Override
	public MasterVariantQuery buildQuery(List<QueryStringTerm> searchTerms) {

		// set fuzziness
		String fuzzySetting = querySettings.get(fuzziness);
		Fuzziness fuzziness = Fuzziness.AUTO;
		if (fuzzySetting != null) {
			Optional<Number> edits = Util.tryToParseAsNumber(fuzzySetting);
			if (edits.isPresent()) {
				fuzziness = Fuzziness.fromEdits(edits.get().intValue());
			}
			// if fuzziness is set explicitly, append fuzzy operator (~) to each
			// term
			searchTerms.forEach(word -> {
				if (word instanceof WeightedWord) {
					((WeightedWord) word).setFuzzy(true);
				}
			});
		}

		String queryString = buildQueryString(searchTerms);
		QueryStringQueryBuilder esQuery = QueryBuilders.queryStringQuery(queryString)
				.minimumShouldMatch(querySettings.getOrDefault(minShouldMatch, null))
				.analyzer(querySettings.getOrDefault(analyzer, null))
				.quoteAnalyzer("whitespace")
				.fuzziness(fuzziness)
				.defaultOperator(Operator.fromString(querySettings.getOrDefault(operator, "OR")))
				.tieBreaker(Util.tryToParseAsNumber(querySettings.getOrDefault(tieBreaker, "0")).orElse(0).floatValue())
				.autoGenerateSynonymsPhraseQuery(false);

		// set type
		Type searchType = Type.valueOf(querySettings.getOrDefault(multimatch_type, Type.CROSS_FIELDS.name())
				.toUpperCase());
		esQuery.type(searchType);

		// ex: best_fields_2<70%(braune damenhose)
		esQuery.queryName(
				(name != null ? name
						: searchType.name().toLowerCase()
								+ (esQuery.minimumShouldMatch() != null ? "_" + esQuery.minimumShouldMatch() : ""))
						+ "(" + queryString + ")");

		for (Entry<String, Float> fieldWeight : weightedFields.entrySet()) {
			esQuery.field(FieldConstants.SEARCH_DATA + "." + fieldWeight.getKey(), fieldWeight.getValue());
		}

		return new MasterVariantQuery(esQuery,
				// boost variants with more matching words
				QueryBuilders.queryStringQuery(queryString)
						.analyzer(querySettings.get(analyzer))
						.defaultField(FieldConstants.VARIANTS + "." + FieldConstants.SEARCH_DATA + ".*"),
				!"0".equals(fuzzySetting),
				Boolean.parseBoolean(querySettings.getOrDefault(acceptNoResult, "false")));
	}

	private String buildQueryString(List<QueryStringTerm> searchTerms) {
		String queryString;
		if (querySettings.getOrDefault(isQueryWithShingles, "false").equalsIgnoreCase("true")) {
			StringBuilder queryStringBuilder = new StringBuilder();
			queryStringBuilder
					.append('(')
					.append(ESQueryUtils.buildQueryString(searchTerms, " "))
					.append(')');
			for (int i = 0; i < searchTerms.size() - 1; i++) {
				List<QueryStringTerm> shingledSearchTerms = new ArrayList<>(searchTerms);
				QueryStringTerm wordA = shingledSearchTerms.remove(i);
				// shingled word has double weight, since it practically matches
				// two input tokens
				WeightedWord shingleWord = new WeightedWord(wordA.getWord() + searchTerms.get(i + 1).getWord(), 2f);
				shingleWord.setFuzzy(wordA instanceof WeightedWord && ((WeightedWord) wordA).isFuzzy());
				shingledSearchTerms.set(i, shingleWord);
				queryStringBuilder.append(" OR ")
						.append('(')
						.append(ESQueryUtils.buildQueryString(shingledSearchTerms, " "))
						.append(")^0.9");
			}
			queryString = queryStringBuilder.toString();
		}
		else {
			queryString = ESQueryUtils.buildQueryString(searchTerms, " ");
		}
		return queryString;
	}

	@Override
	public boolean allowParallelSpellcheckExecution() {
		return Boolean.parseBoolean(querySettings.getOrDefault(allowParallelSpellcheck, "true"));
	}

}
