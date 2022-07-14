package de.cxp.ocs.elasticsearch.query.analyzer;

import java.io.*;
import java.net.URL;
import java.util.*;

import de.cxp.ocs.elasticsearch.query.model.*;
import de.cxp.ocs.spi.search.ConfigurableExtension;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import querqy.model.*;
import querqy.model.Clause.Occur;
import querqy.parser.QuerqyParser;
import querqy.parser.WhiteSpaceQuerqyParser;
import querqy.rewrite.RewriteChain;
import querqy.rewrite.RewriterFactory;
import querqy.rewrite.commonrules.SimpleCommonRulesRewriterFactory;
import querqy.rewrite.commonrules.WhiteSpaceQuerqyParserFactory;
import querqy.rewrite.commonrules.select.ExpressionCriteriaSelectionStrategyFactory;
import querqy.rewrite.experimental.LocalSearchEngineRequestAdapter;

@Slf4j
public class QuerqyQueryExpander implements UserQueryAnalyzer, ConfigurableExtension {

	private final QuerqyParser	parser			= new WhiteSpaceQuerqyParser();
	private RewriteChain		rewriteChain	= null;

	@Override
	public void initialize(Map<String, String> settings) {
		String commonRulesLocation = settings == null ? null : settings.get("common_rules_url");
		try {
			if (commonRulesLocation == null) {
				log.error("no 'common_rules_url' provided! Won't enrich queries with querqy.");
			}
			else if (commonRulesLocation.startsWith("http")) {
				URL url = new URL(commonRulesLocation);
				BufferedInputStream resourceStream = new BufferedInputStream(url.openStream());
				rewriteChain = initFromStream(resourceStream);
			}
			else {
				InputStream resourceStream;

				File rulesFile = new File(commonRulesLocation);
				if (rulesFile.exists()) {
					resourceStream = new FileInputStream(rulesFile);
				}
				else {
					resourceStream = this.getClass().getClassLoader().getResourceAsStream(commonRulesLocation);
				}

				if (resourceStream != null) {
					rewriteChain = initFromStream(resourceStream);
				}
				else {
					log.error("resource '{}' not found. querqy rewriter not initialized", commonRulesLocation);
				}
			}
		}
		catch (Exception e) {
			log.error("Failed to load common rules from url {}", commonRulesLocation, e);
		}
		if (rewriteChain != null) {
			log.info("Successfully initialized querqy from rules at {}", commonRulesLocation);
		}
	}

	private RewriteChain initFromStream(InputStream resourceStream) throws IOException {
		List<RewriterFactory> factories;
		factories = Collections.singletonList(
				new SimpleCommonRulesRewriterFactory(
						"common_rules",
						new InputStreamReader(resourceStream),
						true,
						new WhiteSpaceQuerqyParserFactory(),
						true,
						Collections.emptyMap(),
						new ExpressionCriteriaSelectionStrategyFactory(), false));
		return new RewriteChain(factories);
	}

	@Override
	public List<QueryStringTerm> analyze(String userQuery) {
		// TODO: add extension point for "QueryExpander" and to be like that
		// if a different analyzer is used, it should also be possible to
		// construct an expanded query from a list of QueryStringTerm-s
		ExpandedQuery expandedQuery = new ExpandedQuery(parser.parse(userQuery));
		if (rewriteChain != null) {
			rewriteChain.rewrite(expandedQuery, new LocalSearchEngineRequestAdapter(rewriteChain, Collections.emptyMap()));
		}
		return getQueryTerms(expandedQuery);
	}

	private static List<QueryStringTerm> getQueryTerms(ExpandedQuery expandedQuery) {
		List<QueryStringTerm> terms = new ArrayList<>();
		TermFetcher termFetcher = new TermFetcher();
		if (expandedQuery.getUserQuery() instanceof querqy.model.Query) {
			querqy.model.Query userQuery = (querqy.model.Query) expandedQuery.getUserQuery();

			for (BooleanClause clause : userQuery.getClauses()) {
				clause.accept(termFetcher);
				List<QueryStringTerm> fetchedWords = termFetcher.getWords();
				if (fetchedWords.isEmpty()) continue;

				if (fetchedWords.size() == 1) {
					terms.add(termFetcher.getWords().get(0));
				}
				else {
					List<WeightedWord> convertedList = new ArrayList<>();
					for (int index = 1; index < fetchedWords.size(); index++){
						QueryStringTerm fetchedWord = fetchedWords.get(index);
						if (fetchedWord instanceof WeightedWord) {
							convertedList.add((WeightedWord) fetchedWords.get(index));
						} else {
							log.warn("Found wrong QueryStringTerm entry, cannot add it to the list: " + fetchedWord.toString());
						}
					}

					terms.add(new WordAssociation(fetchedWords.get(0).getWord(), convertedList));
				}
				termFetcher.getWords().clear();

			}
		}
		else if (expandedQuery.getUserQuery() != null) {
			log.error("not expected userquery of type" + expandedQuery.getUserQuery().getClass().getCanonicalName());
		}

		if (expandedQuery.getFilterQueries() != null) {
			Collection<QuerqyQuery<?>> filterQueries = expandedQuery.getFilterQueries();
			for (QuerqyQuery<?> qq : filterQueries) {
				qq.accept(termFetcher);
				terms.addAll(termFetcher.getWords());
				termFetcher.words.clear();
			}
		}

		return terms;
	}

	static class TermFetcher extends AbstractNodeVisitor<Node> {

		@Getter
		List<QueryStringTerm>	words	= new ArrayList<>();
		private Occur			occur;

		@Override
		public Node visit(BooleanQuery booleanQuery) {
			if(booleanQuery.getClauses() != null &&  booleanQuery.getClauses().size() > 1) {

				StringBuilder finalTermValue = new StringBuilder();
				float boost = 0.0f;

				for (BooleanClause clause : booleanQuery.getClauses()) {
					if (clause instanceof DisjunctionMaxQuery) {

						this.occur = clause.getOccur();
						List<?> innerClauses = ((DisjunctionMaxQuery) clause).getClauses();

						if (innerClauses != null && innerClauses.size() > 0 && innerClauses.get(0) instanceof Term) {
							Term termClause = (Term) innerClauses.get(0);

							if(termClause instanceof BoostedTerm) {
								boost = ((BoostedTerm) termClause).getBoost();
							}
							String termClauseValue = termClause.getValue().toString();
							finalTermValue.append(termClauseValue);
							finalTermValue.append(" ");
						}
					}
				}

				if(finalTermValue.length() > 0) {
					finalTermValue.deleteCharAt(finalTermValue.length() - 1);
				}

				WeightedWord weightedWord;
				if (boost != 0.0f) {
					weightedWord = new WeightedWord(finalTermValue.toString(), boost);
				} else {
					weightedWord = new WeightedWord(finalTermValue.toString());
				}
				if (occur != null) {
					weightedWord.setOccur(org.apache.lucene.search.BooleanClause.Occur.valueOf(occur.name()));
					// guard MUST/NOT terms from analyzer
					if (occur.equals(Occur.MUST) || occur.equals(Occur.MUST_NOT)) {
						weightedWord.setQuoted(true);
					}
					this.occur = null;
				}
				words.add(weightedWord);

				return null;

			} else {
				return super.visit(booleanQuery);
			}
		}

		@Override
		public Node visit(DisjunctionMaxQuery disjunctionMaxQuery) {
			this.occur = disjunctionMaxQuery.occur;
			return super.visit(disjunctionMaxQuery);
		}

		@Override
		public Node visit(RawQuery rawQuery) {
			this.occur = rawQuery.occur;
			String rawQueryString = ((StringRawQuery) rawQuery).getQueryString();

			if (rawQueryString.indexOf(':') > 0) {

				if(rawQueryString.indexOf(' ') > 0) {
					String[] rawFiltersSplit = rawQueryString.split(" ");
					for(String rawFilter: rawFiltersSplit) {
						if(rawFilter.indexOf(':') > 0) {
							QueryFilterTerm queryFilterTerm = getQueryFilterTerm(rawFilter);
							words.add(queryFilterTerm);
						} else {
							words.add(new RawQueryString(rawQueryString));
						}
					}
				} else {
					QueryFilterTerm queryFilterTerm = getQueryFilterTerm(rawQueryString);
					words.add(queryFilterTerm);
				}
			}
			else {
				words.add(new RawQueryString(rawQueryString));
			}
			return super.visit(rawQuery);
		}

		private QueryFilterTerm getQueryFilterTerm(String rawQueryString) {

			String[] rawQuerySplit = rawQueryString.split(":");
			// TODO: Make it possible to use " AND " as a conjunction for
			// these values
			String fieldName = rawQuerySplit[0];
			String value = rawQuerySplit[1];

			org.apache.lucene.search.BooleanClause.Occur occur;
			if(Occur.MUST_NOT.equals(this.occur)) {
				occur = org.apache.lucene.search.BooleanClause.Occur.MUST_NOT;
			} else {
				occur = org.apache.lucene.search.BooleanClause.Occur.MUST;
			}

			QueryFilterTerm queryFilterTerm = new QueryFilterTerm(fieldName, value);
			queryFilterTerm.setOccur(occur);

			return queryFilterTerm;
		}

		@Override
		public Node visit(Term term) {
			WeightedWord weightedWord;
			if (term instanceof BoostedTerm) {
				weightedWord = new WeightedWord(term.getValue().toString(), ((BoostedTerm) term).getBoost());
			}
			else {
				weightedWord = new WeightedWord(term.toString());
			}
			if (occur != null) {
				weightedWord.setOccur(org.apache.lucene.search.BooleanClause.Occur.valueOf(occur.name()));

				// guard MUST/NOT terms from analyzer
				if (occur.equals(Occur.MUST) || occur.equals(Occur.MUST_NOT)) {
					weightedWord.setQuoted(true);
				}
				occur = null;
			}
			words.add(weightedWord);

			return super.visit(term);
		}
	}
}
