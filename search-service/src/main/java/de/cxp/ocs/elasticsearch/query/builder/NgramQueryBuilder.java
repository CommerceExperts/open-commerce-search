package de.cxp.ocs.elasticsearch.query.builder;

import static de.cxp.ocs.config.QueryBuildingSetting.acceptNoResult;
import static de.cxp.ocs.config.QueryBuildingSetting.minShouldMatch;
import static de.cxp.ocs.config.QueryBuildingSetting.multimatch_type;
import static de.cxp.ocs.config.QueryBuildingSetting.tieBreaker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.QueryBuilders;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.util.ESQueryUtils;
import de.cxp.ocs.util.Util;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * builds a query that uses the ngram fields to handle decomposition and
 * fuzziness
 */
@Slf4j
public class NgramQueryBuilder implements ESQueryBuilder {

	private final Map<QueryBuildingSetting, String>	querySettings;
	private final Map<String, Float>				masterFields	= new HashMap<>();
	private final Map<String, Float>				variantFields	= new HashMap<>();

	@Getter
	@Setter
	private String name;

	public NgramQueryBuilder(Map<QueryBuildingSetting, String> settings, @NonNull Map<String, Float> searchableFields,
			Map<String, Field> fields) {
		this.querySettings = settings;
		for (Entry<String, Float> fieldAndWeight : searchableFields.entrySet()) {
			String fieldName = fieldAndWeight.getKey().split("\\.")[0].replaceAll("[^a-zA-Z0-9-_]", "");
			Field fieldConf = fields.get(fieldName);
			if (fieldConf == null) {
				log.warn("field with stripped name {} (from {}) not found. Ignoring.", fieldName, fieldAndWeight
						.getKey());
				continue;
			}
			if (fieldConf.getUsage().contains(FieldUsage.Search)) {
				if (fieldConf.isVariantLevel()) {
					variantFields.put(
							FieldConstants.VARIANTS + "." + FieldConstants.SEARCH_DATA + "." + fieldName + ".ngram",
							fieldAndWeight.getValue());
				}
				if (fieldConf.isMasterLevel()) {
					masterFields.put(
							FieldConstants.SEARCH_DATA + "." + fieldName + ".ngram",
							fieldAndWeight.getValue());
				}
			}
		}

	}

	@Override
	public MasterVariantQuery buildQuery(List<QueryStringTerm> searchTerms) {
		StringBuilder searchPhrase = new StringBuilder();
		// TODO: do a ngram tokenization on each term separately and search each
		// "ngramed word" separately combined with boolean-should-clauses but
		// each using "best-field" match strategy.
		// minShouldMatch setting is then used twice: per splitted field and for
		// the words itself (the should clauses)
		searchTerms.forEach(term -> searchPhrase.append(term.getWord()).append(" "));

		// TODO: use locale for lowercase or better: use elasticsearch analyzer!
		String ngramPhrase = buildNgrams(searchPhrase.toString().trim().toLowerCase());

		MultiMatchQueryBuilder mainQuery = buildEsQuery(ngramPhrase);
		if (masterFields.size() > 0) {
			mainQuery.fields(masterFields);
		}
		String queryName = getLabel(searchTerms);
		mainQuery.queryName(queryName);

		MultiMatchQueryBuilder variantQuery = buildEsQuery(ngramPhrase);
		if (variantFields.size() > 0) {
			variantQuery.fields(variantFields);
		}
		variantQuery.queryName("variant-" + queryName);

		// isWithSpellCorrect=true because ngram is kind of a fuzzy matching
		return new MasterVariantQuery(mainQuery, variantQuery, true,
				Boolean.parseBoolean(querySettings.getOrDefault(acceptNoResult, "true")));
	}

	private String getLabel(List<QueryStringTerm> searchTerms) {
		if (name == null) {
			name = "ngram-" + getMinShouldMatch();
		}
		return name + "(" + ESQueryUtils.getQueryLabel(searchTerms) + ")";
	}

	private MultiMatchQueryBuilder buildEsQuery(String ngramPhrase) {
		MultiMatchQueryBuilder mainQuery = QueryBuilders
				.multiMatchQuery(ngramPhrase)
				.analyzer("whitespace")
				.fuzziness(Fuzziness.ZERO)
				.minimumShouldMatch(getMinShouldMatch())
				.tieBreaker(Util.tryToParseAsNumber(querySettings.getOrDefault(tieBreaker, "0")).orElse(0).floatValue())
				.type(Type.valueOf(querySettings.getOrDefault(multimatch_type, Type.BEST_FIELDS.name())
						.toUpperCase()));
		return mainQuery;
	}

	private String getMinShouldMatch() {
		return querySettings.getOrDefault(minShouldMatch, "70%");
	}

	protected String buildNgrams(String searchPhrase) {
		if (searchPhrase == null || searchPhrase.length() < 2) return searchPhrase;

		List<String> ngrams = new ArrayList<>();
		ngrams.add("_" + searchPhrase.substring(0, 2));

		// in: damenkleider
		// out: _da dam ame men enk nkl kle lei eid ide der er_
		for (int i = 0; i < searchPhrase.length() - 2; i++) {
			boolean foundWhiteSpace = false;
			if (searchPhrase.charAt(i) == ' ') {
				if (i + 3 <= searchPhrase.length()) {
					ngrams.add("_" + searchPhrase.substring(i + 1, i + 3));
				}
				foundWhiteSpace = true;
			}
			if (searchPhrase.charAt(i + 2) == ' ') {
				ngrams.add(searchPhrase.substring(i, i + 2) + "_");
				i++;
				foundWhiteSpace = true;
			}
			if (!foundWhiteSpace) {
				ngrams.add(searchPhrase.substring(i, i + 3));
			}
		}
		ngrams.add(searchPhrase.substring(searchPhrase.length() - 2) + "_");
		return StringUtils.join(ngrams, " ");
	}

	@Override
	public boolean allowParallelSpellcheckExecution() {
		return false;
	}
}
