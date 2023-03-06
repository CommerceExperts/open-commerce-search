package de.cxp.ocs.elasticsearch;

import java.util.*;

import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.Suggest.Suggestion;
import org.elasticsearch.search.suggest.Suggest.Suggestion.Entry;
import org.elasticsearch.search.suggest.Suggest.Suggestion.Entry.Option;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;

import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;
import de.cxp.ocs.elasticsearch.query.model.WeightedWord;
import de.cxp.ocs.elasticsearch.query.model.WordAssociation;
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

	public Map<String, WordAssociation> extractRelatedWords(Collection<QueryStringTerm> searchWords, Suggest suggest) {
		Map<String, WordAssociation> correctedWords = new HashMap<>();
		Map<String, Float> bestScores = new HashMap<>();

		for (Suggestion<? extends Entry<? extends Option>> correction : suggest) {
			for (Entry<? extends Option> perWordCorrection : correction.getEntries()) {
				for (Option spellCorrectOption : perWordCorrection.getOptions()) {
					float bestScore = bestScores.getOrDefault(perWordCorrection.getText().string(), 0f);
					if (spellCorrectOption.getScore() >= minScore && spellCorrectOption.getScore() >= bestScore) {
						WordAssociation wordCorrections = correctedWords
								.computeIfAbsent(perWordCorrection.getText().string(), WordAssociation::new);

						if (spellCorrectOption.getScore() > bestScore) {
							bestScores.put(perWordCorrection.getText().string(), spellCorrectOption.getScore());
							if (wordCorrections.getRelatedWords().size() > 0) {
								wordCorrections.getRelatedWords().clear();
							}
						}

						wordCorrections.putOrUpdate(
								new WeightedWord(
										spellCorrectOption.getText().string(),
										spellCorrectOption.getScore()));
						// XXX was this used before?
						//((org.elasticsearch.search.suggest.term.TermSuggestion.Entry.Option) spellCorrectOption).getFreq())
					}
				}
			}
		}

		return correctedWords;
	}

	public static List<QueryStringTerm> toListWithAllTerms(Collection<QueryStringTerm> searchWords,
			Map<String, WordAssociation> correctedWords) {
		List<QueryStringTerm> relatedWords = new ArrayList<>();
		for (QueryStringTerm searchWord : searchWords) {
			WordAssociation correctedWord = correctedWords.get(searchWord.getWord());
			if (correctedWord == null) {
				relatedWords.add(searchWord);
			}
			else {
				relatedWords.add(correctedWord);
			}
		}
		return relatedWords;
	}
}
