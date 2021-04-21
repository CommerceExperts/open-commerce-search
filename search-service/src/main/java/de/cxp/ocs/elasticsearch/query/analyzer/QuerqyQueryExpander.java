package de.cxp.ocs.elasticsearch.query.analyzer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WeightedWord;
import de.cxp.ocs.elasticsearch.query.model.WordAssociation;
import de.cxp.ocs.spi.search.ConfigurableExtension;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import querqy.model.AbstractNodeVisitor;
import querqy.model.BooleanClause;
import querqy.model.BooleanQuery;
import querqy.model.BoostedTerm;
import querqy.model.Clause.Occur;
import querqy.model.DisjunctionMaxQuery;
import querqy.model.ExpandedQuery;
import querqy.model.Node;
import querqy.model.QuerqyQuery;
import querqy.model.Term;
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
				InputStream resourceStream = this.getClass().getClassLoader().getResourceAsStream(commonRulesLocation);
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
				List<WeightedWord> fetchedWords = termFetcher.getWords();
				if (fetchedWords.isEmpty()) continue;

				if (fetchedWords.size() == 1) {
					terms.add(termFetcher.getWords().get(0));
				}
				else {
					terms.add(new WordAssociation(fetchedWords.get(0).getWord(), new ArrayList(fetchedWords.subList(1, fetchedWords.size()))));
				}
				termFetcher.getWords().clear();

			}
		}
		else if (expandedQuery.getUserQuery() != null) {
			log.error("not expected userquery of type" + expandedQuery.getUserQuery().getClass().getCanonicalName());
		}

		if (expandedQuery.getFilterQueries() != null) {
			Collection<QuerqyQuery<?>> filterQueries = expandedQuery.getFilterQueries();
			for (QuerqyQuery qq : filterQueries) {
				qq.accept(termFetcher);
				terms.addAll(termFetcher.getWords());
				termFetcher.words.clear();
			}
		}

		return terms;
	}

	static class TermFetcher extends AbstractNodeVisitor<Node> {

		@Getter
		List<WeightedWord>	words	= new ArrayList<>();
		private Occur		occur;

		@Override
		public Node visit(BooleanQuery booleanQuery) {
			this.occur = booleanQuery.occur;
			return super.visit(booleanQuery);
		}

		@Override
		public Node visit(DisjunctionMaxQuery disjunctionMaxQuery) {
			this.occur = disjunctionMaxQuery.occur;
			return super.visit(disjunctionMaxQuery);
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
				occur = null;
			}
			words.add(weightedWord);

			return super.visit(term);
		}
	}
}
