package de.cxp.ocs.elasticsearch;

import java.util.*;

import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction.Modifier;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery.ScoreMode;
import org.elasticsearch.index.query.functionscore.*;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder.FilterFunctionBuilder;
import org.elasticsearch.script.Script;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.config.*;
import de.cxp.ocs.config.ScoringConfiguration.ScoringFunction;
import de.cxp.ocs.elasticsearch.query.ScoringContext;
import de.cxp.ocs.util.ConfigurationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScoringCreator {

	private final ScoringConfiguration	scoreConf;
	private final List<ScoringFunction>	scoreFunctions;
	private final Map<String, Field>	scoreFields;

	public ScoringCreator(SearchContext context) {
		scoreConf = context.config.getScoring();
		// copy into array, so that we can remove invalid score definitions
		scoreFunctions = new ArrayList<>(scoreConf.getScoreFunctions());
		Map<String, Field> tempScoreFields = context.getFieldConfigIndex().getFieldsByUsage(FieldUsage.SCORE);
		scoreFields = Collections.unmodifiableMap(tempScoreFields);
	}

	public ScoringContext getScoringContext() {
		ScoringContext scoringContext = new ScoringContext();
		scoringContext.setBoostMode(CombineFunction.fromString(scoreConf.getBoostMode().name().toUpperCase()));
		scoringContext.setScoreMode(ScoreMode.fromString(scoreConf.getScoreMode().name().toUpperCase()));

		Iterator<ScoringFunction> scoreFunctionIterator = scoreFunctions.iterator();
		while (scoreFunctionIterator.hasNext()) {
			ScoringFunction scoringFunction = scoreFunctionIterator.next();

			boolean isVariantScoringField = Optional.ofNullable(scoringFunction.getField()).map(scoreFields::get).map(Field::isVariantLevel).orElse(false);
			// if this is a scoring function based on a variant field, then it should be used per default for
			// variant-based scoring. All other scoring functions are not used for variant scoring unless defined
			// explicitly.
			boolean useForVariants = Boolean.parseBoolean(scoringFunction.getOptions().getOrDefault(ScoreOption.USE_FOR_VARIANTS, Boolean.toString(isVariantScoringField)));
			if (useForVariants && !isVariantScoringField) {
				log.warn("score function configured for variant level on field {}, but field does not exist on variant level."
						+ " Discarding function {}", scoringFunction.getField(), scoringFunction);
				scoreFunctionIterator.remove();
				continue;
			}
			// FIXME: a scoring function that exists on both level, should also be applied on both levels.

			try {
				switch (scoringFunction.getType()) {
					case SCRIPT_SCORE:
						buildScriptScoreFunction(scoringFunction)
								.ifPresent(f -> scoringContext.addScoringFunction(new FilterFunctionBuilder(f), useForVariants));
						break;
					case WEIGHT:
						scoringContext.addScoringFunction(
								new FilterFunctionBuilder(ScoreFunctionBuilders.weightFactorFunction(scoringFunction.getWeight())),
								useForVariants);
						break;
					case RANDOM_SCORE:
						buildRandomScoreFunction(scoringFunction, scoreFields.get(scoringFunction.getField()), useForVariants)
								.ifPresent(f -> scoringContext.addScoringFunction(new FilterFunctionBuilder(f), useForVariants));
						break;
					default:
						buildFieldBasedScoreFunction(scoringFunction, scoreFields.get(scoringFunction.getField()), useForVariants)
								.ifPresent(f -> scoringContext.addScoringFunction(new FilterFunctionBuilder(f), useForVariants));
				}
			}
			catch (ConfigurationException configException) {
				log.error("Applying scoring function failed: {}! Will remove it until next configuration reload.", configException.getMessage());
				scoreFunctionIterator.remove();
			}
		}
		return scoringContext;
	}

	private Optional<ScoreFunctionBuilder<?>> buildFieldBasedScoreFunction(ScoringFunction scoringFunction, Field field, boolean isForVariantLevel) throws ConfigurationException {
		// validate if field exists
		Field scoreField = scoreFields.get(scoringFunction.getField());
		if (scoreField == null) {
			throw new ConfigurationException("tried to score on unscorable field " + scoringFunction);
		}

		ScoreFunctionBuilder<?> function = null;
		// check if is applicable for variant level
		if (isForVariantLevel && scoreField.isVariantLevel() || !isForVariantLevel && scoreField.isMasterLevel()) {
			String fullFieldName = getFullName(scoreField, isForVariantLevel);
			switch (scoringFunction.getType()) {
				case FIELD_VALUE_FACTOR:
					function = getFieldValueFactorFunction(scoringFunction, fullFieldName);
					break;
				case DECAY_EXP:
				case DECAY_GAUSS:
				case DECAY_LINEAR:
					Map<ScoreOption, String> options = scoringFunction.getOptions();
					if (!options.containsKey(ScoreOption.ORIGIN)) {
						throw new ConfigurationException("missing required option 'origin' for " + scoringFunction.getType() + " scoring on field " + scoringFunction.getField());
					}
					if (!options.containsKey(ScoreOption.SCALE)) {
						throw new ConfigurationException("missing required option 'scale' for " + scoringFunction.getType() + " scoring on field " + scoringFunction.getField());
					}

					DecayFunctionBuilder<?> decayFunct = null;
					if (ScoreType.DECAY_EXP.equals(scoringFunction.getType())) {
						decayFunct = ScoreFunctionBuilders.exponentialDecayFunction(
								fullFieldName,
								options.get(ScoreOption.ORIGIN),
								options.get(ScoreOption.SCALE),
								options.getOrDefault(ScoreOption.OFFSET, "0"),
								Double.parseDouble(options.getOrDefault(ScoreOption.DECAY, "0.5")));
					}
					else if (ScoreType.DECAY_GAUSS.equals(scoringFunction.getType())) {
						decayFunct = ScoreFunctionBuilders.gaussDecayFunction(
								fullFieldName,
								options.get(ScoreOption.ORIGIN),
								options.get(ScoreOption.SCALE),
								options.getOrDefault(ScoreOption.OFFSET, "0"),
								Double.parseDouble(options.getOrDefault(ScoreOption.DECAY, "0.5")));
					}
					else {
						decayFunct = ScoreFunctionBuilders.linearDecayFunction(
								fullFieldName,
								options.get(ScoreOption.ORIGIN),
								options.get(ScoreOption.SCALE),
								options.getOrDefault(ScoreOption.OFFSET, "0"),
								Double.parseDouble(options.getOrDefault(ScoreOption.DECAY, "0.5")));
					}
					decayFunct.setWeight(scoringFunction.getWeight());
					function = decayFunct;
					break;
				default:
					throw new ConfigurationException("Score function of type " + scoringFunction.getType() + " not implemented");
			}
		}
		return Optional.ofNullable(function);
	}

	private FieldValueFactorFunctionBuilder getFieldValueFactorFunction(ScoringFunction scoreMethod,
			String fullFieldName) {
		FieldValueFactorFunctionBuilder fieldFunct = ScoreFunctionBuilders.fieldValueFactorFunction(fullFieldName);
		fieldFunct.setWeight(scoreMethod.getWeight());
		Map<ScoreOption, String> options = scoreMethod.getOptions();
		fieldFunct.modifier(Modifier.fromString(options.getOrDefault(ScoreOption.MODIFIER, "NONE")));
		fieldFunct.factor(Float.parseFloat(options.getOrDefault(ScoreOption.FACTOR, "1")));
		fieldFunct.missing(Double.parseDouble(options.getOrDefault(ScoreOption.MISSING, "0")));
		return fieldFunct;
	}

	private Optional<RandomScoreFunctionBuilder> buildRandomScoreFunction(ScoringFunction scoreMethod,
			Field field, boolean isForVariants) throws ConfigurationException {
		RandomScoreFunctionBuilder randomFunction = ScoreFunctionBuilders.randomFunction()
				.setWeight(scoreMethod.getWeight());
		Object randomSeed = scoreMethod.getOptions().get(ScoreOption.RANDOM_SEED);
		if (randomSeed != null) {
			if (field == null) {
				throw new ConfigurationException("Random Score Function with seed requires a configured field, but non configured.");
			}

			// if referencing a field, the score function becomes
			// variant-sensitive
			if (isForVariants && !field.isVariantLevel()
					|| !isForVariants && !field.isMasterLevel()) {
				return Optional.empty();
			}

			String fullFieldName = getFullName(field, isForVariants);
			randomFunction.seed(randomSeed.toString());
			randomFunction.setField(fullFieldName);
		}
		return Optional.of(randomFunction);
	}

	private Optional<ScriptScoreFunctionBuilder> buildScriptScoreFunction(ScoringFunction scoringFunction) throws ConfigurationException {
		String scriptCode = scoringFunction.getOptions().get(ScoreOption.SCRIPT_CODE);
		if (scriptCode == null || scriptCode.isEmpty()) {
			throw new ConfigurationException("Configured script score function has no 'script_code' defined!");
		}
		Script script = new Script(scriptCode);
		ScriptScoreFunctionBuilder scriptScoreFunctionBuilder = new ScriptScoreFunctionBuilder(script);
		scriptScoreFunctionBuilder.setWeight(scoringFunction.getWeight());
		return Optional.of(scriptScoreFunctionBuilder);
	}

	private String getFullName(Field scoreField, boolean isForVariantLevel) {
		return (isForVariantLevel ? FieldConstants.VARIANTS + "." : "")
				+ FieldConstants.SCORES + "." + scoreField.getName();
	}

}
