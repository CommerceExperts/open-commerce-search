package de.cxp.ocs.elasticsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction.Modifier;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery.ScoreMode;
import org.elasticsearch.index.query.functionscore.DecayFunctionBuilder;
import org.elasticsearch.index.query.functionscore.FieldValueFactorFunctionBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder.FilterFunctionBuilder;
import org.elasticsearch.index.query.functionscore.RandomScoreFunctionBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.config.InternalSearchConfiguration;
import de.cxp.ocs.config.ScoreOption;
import de.cxp.ocs.config.ScoreType;
import de.cxp.ocs.config.ScoringConfiguration;
import de.cxp.ocs.config.ScoringConfiguration.ScoringFunction;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScoringCreator {

	private final ScoringConfiguration scoreConf;

	private final Map<String, Field> scoreFields;

	public ScoringCreator(InternalSearchConfiguration config) {
		scoreConf = config.provided.getScoring();
		Map<String, Field> tempScoreFields = config.getFieldConfigIndex().getFieldsByUsage(FieldUsage.Score);
		scoreFields = Collections.unmodifiableMap(tempScoreFields);
	}

	public FilterFunctionBuilder[] getScoringFunctions(boolean isForVariantLevel) {
		List<FilterFunctionBuilder> filterFunctionBuilders = new ArrayList<>();
		for (ScoringFunction scoringFunct : scoreConf.getScoreFunctions()) {
			Field scoreField = scoreFields.get(scoringFunct.getField());
			if (scoreField == null) {
				log.warn("tried to score on an unscorable field: " + scoringFunct);
				continue;
			}
			if (isForVariantLevel && scoreField.isVariantLevel()
					|| !isForVariantLevel && scoreField.isMasterLevel()) {
				switch (scoringFunct.getType()) {
					case field_value_factor:
						filterFunctionBuilders.add(
								new FilterFunctionBuilder(
										getFieldValueFactorFunction(scoringFunct,
												getFullName(scoreField, isForVariantLevel))));
						break;
					case random_score:
						filterFunctionBuilders.add(
								new FilterFunctionBuilder(
										getRandomScoreFunction(scoringFunct,
												getFullName(scoreField, isForVariantLevel))));
						break;
					case decay_exp:
					case decay_gauss:
					case decay_linear:
						Map<ScoreOption, String> options = scoringFunct.getOptions();
						if (!options.containsKey(ScoreOption.origin) || !options.containsKey(ScoreOption.scale)) {
							log.warn(
									"not all required option (origin + scale) for decay scoring on field {} are present: {}",
									options.toString());
							break;
						}

						DecayFunctionBuilder<?> decayFunct = null;
						if (ScoreType.decay_exp.equals(scoringFunct.getType())) {
							decayFunct = ScoreFunctionBuilders.exponentialDecayFunction(
									getFullName(scoreField, isForVariantLevel),
									options.get(ScoreOption.origin),
									options.get(ScoreOption.scale),
									options.getOrDefault(ScoreOption.offset, "0"),
									Double.parseDouble(options.getOrDefault(ScoreOption.decay, "0.5")));
						}
						else if (ScoreType.decay_gauss.equals(scoringFunct.getType())) {
							decayFunct = ScoreFunctionBuilders.gaussDecayFunction(
									getFullName(scoreField, isForVariantLevel),
									options.get(ScoreOption.origin),
									options.get(ScoreOption.scale),
									options.getOrDefault(ScoreOption.offset, "0"),
									Double.parseDouble(options.getOrDefault(ScoreOption.decay, "0.5")));
						}
						else {
							decayFunct = ScoreFunctionBuilders.linearDecayFunction(
									getFullName(scoreField, isForVariantLevel),
									options.get(ScoreOption.origin),
									options.get(ScoreOption.scale),
									options.getOrDefault(ScoreOption.offset, "0"),
									Double.parseDouble(options.getOrDefault(ScoreOption.decay, "0.5")));
						}
						decayFunct.setWeight(scoringFunct.getWeight());

						filterFunctionBuilders.add(new FilterFunctionBuilder(decayFunct));
						break;
					case weight:
					default:
						filterFunctionBuilders.add(
								new FilterFunctionBuilder(
										ScoreFunctionBuilders.weightFactorFunction(
												scoringFunct.getWeight())));
				}
			}
		}
		return filterFunctionBuilders.toArray(new FilterFunctionBuilder[filterFunctionBuilders.size()]);
	}

	private RandomScoreFunctionBuilder getRandomScoreFunction(ScoringFunction scoreMethod,
			String fullFieldName) {
		RandomScoreFunctionBuilder randomFunction = ScoreFunctionBuilders.randomFunction()
				.setWeight(scoreMethod.getWeight());
		Object randomSeed = scoreMethod.getOptions().get(ScoreOption.random_seed);
		if (randomSeed != null) {
			randomFunction.seed(randomSeed.toString());
			randomFunction.setField(fullFieldName);
		}
		return randomFunction;
	}

	private FieldValueFactorFunctionBuilder getFieldValueFactorFunction(ScoringFunction scoreMethod,
			String fullFieldName) {
		FieldValueFactorFunctionBuilder fieldFunct = ScoreFunctionBuilders.fieldValueFactorFunction(fullFieldName);
		fieldFunct.setWeight(scoreMethod.getWeight());
		Map<ScoreOption, String> options = scoreMethod.getOptions();
		fieldFunct.modifier(Modifier.fromString(options.getOrDefault(ScoreOption.modifier, "NONE")));
		fieldFunct.missing(Double.parseDouble(options.getOrDefault(ScoreOption.missing, "0")));
		return fieldFunct;
	}

	private String getFullName(Field scoreField, boolean isForVariantLevel) {
		return (isForVariantLevel ? FieldConstants.VARIANTS + "." : "")
				+ FieldConstants.SCORES + "." + scoreField.getName();
	}

	public CombineFunction getBoostMode() {
		return CombineFunction.fromString(scoreConf.getBoostMode().name().toUpperCase());
	}

	public ScoreMode getScoreMode() {
		return ScoreMode.fromString(scoreConf.getScoreMode().name().toUpperCase());
	}
}
