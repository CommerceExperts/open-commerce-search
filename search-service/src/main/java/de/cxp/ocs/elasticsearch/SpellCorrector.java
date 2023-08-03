package de.cxp.ocs.elasticsearch;

import java.util.*;

import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.Suggest.Suggestion;
import org.elasticsearch.search.suggest.Suggest.Suggestion.Entry;
import org.elasticsearch.search.suggest.Suggest.Suggestion.Entry.Option;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;

import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.model.query.AnalyzedQuery;
import de.cxp.ocs.elasticsearch.model.query.MultiTermQuery;
import de.cxp.ocs.elasticsearch.model.query.MultiVariantQuery;
import de.cxp.ocs.elasticsearch.model.query.SingleTermQuery;
import de.cxp.ocs.elasticsearch.model.term.AssociatedTerm;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.model.term.WeightedTerm;
import de.cxp.ocs.elasticsearch.query.builder.CountedTerm;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SpellCorrector {

	private final String[]	spellCorrectionFields;
	private float			minScore	= 0.75f;

	public SuggestBuilder buildSpellCorrectionQuery(String userQuery) {
		SuggestBuilder suggestBuilder = new SuggestBuilder().setGlobalText(userQuery);
		for (String fieldName : spellCorrectionFields) {
			suggestBuilder.addSuggestion(fieldName,
					SuggestBuilders
							.termSuggestion(FieldConstants.SEARCH_DATA + "." + fieldName)
							.analyzer("whitespace"));
		}
		return suggestBuilder;
	}

	public Map<String, AssociatedTerm> extractRelatedWords(Suggest suggest) {
		Map<String, AssociatedTerm> correctedWords = new HashMap<>();
		Map<String, Float> bestScores = new HashMap<>();

		for (Suggestion<? extends Entry<? extends Option>> correction : suggest) {
			for (Entry<? extends Option> perWordCorrection : correction.getEntries()) {
				for (Option spellCorrectOption : perWordCorrection.getOptions()) {
					String inputTerm = perWordCorrection.getText().string();
					float bestScore = bestScores.getOrDefault(inputTerm, 0f);
					if (spellCorrectOption.getScore() >= minScore && spellCorrectOption.getScore() >= bestScore) {
						AssociatedTerm wordCorrections = correctedWords.computeIfAbsent(inputTerm, t -> new AssociatedTerm(new WeightedTerm(t)));

						if (spellCorrectOption.getScore() > bestScore) {
							bestScores.put(perWordCorrection.getText().string(), spellCorrectOption.getScore());
							if (wordCorrections.getRelatedTerms().size() > 0) {
								wordCorrections.getRelatedTerms().clear();
							}
						}

						wordCorrections.putOrUpdate(
								new CountedTerm(
										new WeightedTerm(
												spellCorrectOption.getText().string(),
												spellCorrectOption.getScore()),
										((org.elasticsearch.search.suggest.term.TermSuggestion.Entry.Option) spellCorrectOption).getFreq()));
					}
				}
			}
		}

		return correctedWords;
	}

	public static AnalyzedQuery toListWithAllTerms(AnalyzedQuery analyzedQuery, Map<String, AssociatedTerm> correctedWords) {
		if (correctedWords.isEmpty()) return analyzedQuery;

		List<QueryStringTerm> relatedWords = new ArrayList<>();
		for (String inputTerm : analyzedQuery.getInputTerms()) {
			AssociatedTerm correctedWord = correctedWords.get(inputTerm);
			if (correctedWord == null) {
				relatedWords.add(new WeightedTerm(inputTerm));
			}
			else {
				relatedWords.add(correctedWord);
			}
		}

		AnalyzedQuery alternativeQuery = relatedWords.size() == 1 ? new SingleTermQuery(relatedWords.get(0)) : new MultiTermQuery(relatedWords);
		return new MultiVariantQuery(analyzedQuery.getInputTerms(), Arrays.asList(analyzedQuery, alternativeQuery));
	}
}
