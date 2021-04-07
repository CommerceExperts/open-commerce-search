package de.cxp.ocs.elasticsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction.Modifier;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery.ScoreMode;
import org.elasticsearch.index.query.functionscore.DecayFunctionBuilder;
import org.elasticsearch.index.query.functionscore.FieldValueFactorFunctionBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder.FilterFunctionBuilder;
import org.elasticsearch.index.query.functionscore.RandomScoreFunctionBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.index.query.functionscore.ScriptScoreFunctionBuilder;
import org.elasticsearch.script.Script;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.config.ScoreOption;
import de.cxp.ocs.config.ScoreType;
import de.cxp.ocs.config.ScoringConfiguration;
import de.cxp.ocs.config.ScoringConfiguration.ScoringFunction;
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
		Map<String, Field> tempScoreFields = context.getFieldConfigIndex().getFieldsByUsage(FieldUsage.Score);
		scoreFields = Collections.unmodifiableMap(tempScoreFields);
	}

	public FilterFunctionBuilder[] getScoringFunctions(boolean isForVariantLevel) {
		List<FilterFunctionBuilder> filterFunctionBuilders = new ArrayList<>();
		Iterator<ScoringFunction> scoreFunctionIterator = scoreFunctions.iterator();
		while (scoreFunctionIterator.hasNext()) {
			ScoringFunction scoringFunction = scoreFunctionIterator.next();

			try {
				switch (scoringFunction.getType()) {
					case script_score:
						buildScriptScoreFunction(scoringFunction)
								.ifPresent(f -> filterFunctionBuilders.add(new FilterFunctionBuilder(f)));
						break;
					case weight:
						filterFunctionBuilders.add(
								new FilterFunctionBuilder(ScoreFunctionBuilders.weightFactorFunction(scoringFunction.getWeight())));
						break;
					case random_score:
						buildRandomScoreFunction(scoringFunction, scoreFields.get(scoringFunction.getField()), isForVariantLevel)
								.ifPresent(f -> filterFunctionBuilders.add(new FilterFunctionBuilder(f)));
						break;
					default:
						buildFieldBasedScoreFunction(scoringFunction, scoreFields.get(scoringFunction.getField()), isForVariantLevel)
								.ifPresent(f -> filterFunctionBuilders.add(new FilterFunctionBuilder(f)));
				}
			}
			catch (ConfigurationException configException) {
				log.error("Applying scoring function failed: {}! Will remove it until next configuration reload.", configException.getMessage());
				scoreFunctionIterator.remove();
			}
		}
		return filterFunctionBuilders.toArray(new FilterFunctionBuilder[filterFunctionBuilders.size()]);
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
				case field_value_factor:
					function = getFieldValueFactorFunction(scoringFunction, fullFieldName);
					break;
				case decay_exp:
				case decay_gauss:
				case decay_linear:
					Map<ScoreOption, String> options = scoringFunction.getOptions();
					if (!options.containsKey(ScoreOption.origin)) {
						throw new ConfigurationException("missing required option 'origin' for " + scoringFunction.getType() + " scoring on field " + scoringFunction.getField());
					}
					if (!options.containsKey(ScoreOption.scale)) {
						throw new ConfigurationException("missing required option 'scale' for " + scoringFunction.getType() + " scoring on field " + scoringFunction.getField());
					}

					DecayFunctionBuilder<?> decayFunct = null;
					if (ScoreType.decay_exp.equals(scoringFunction.getType())) {
						decayFunct = ScoreFunctionBuilders.exponentialDecayFunction(
								fullFieldName,
								options.get(ScoreOption.origin),
								options.get(ScoreOption.scale),
								options.getOrDefault(ScoreOption.offset, "0"),
								Double.parseDouble(options.getOrDefault(ScoreOption.decay, "0.5")));
					}
					else if (ScoreType.decay_gauss.equals(scoringFunction.getType())) {
						decayFunct = ScoreFunctionBuilders.gaussDecayFunction(
								fullFieldName,
								options.get(ScoreOption.origin),
								options.get(ScoreOption.scale),
								options.getOrDefault(ScoreOption.offset, "0"),
								Double.parseDouble(options.getOrDefault(ScoreOption.decay, "0.5")));
					}
					else {
						decayFunct = ScoreFunctionBuilders.linearDecayFunction(
								fullFieldName,
								options.get(ScoreOption.origin),
								options.get(ScoreOption.scale),
								options.getOrDefault(ScoreOption.offset, "0"),
								Double.parseDouble(options.getOrDefault(ScoreOption.decay, "0.5")));
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
		fieldFunct.modifier(Modifier.fromString(options.getOrDefault(ScoreOption.modifier, "NONE")));
		fieldFunct.missing(Double.parseDouble(options.getOrDefault(ScoreOption.missing, "0")));
		return fieldFunct;
	}

	private Optional<RandomScoreFunctionBuilder> buildRandomScoreFunction(ScoringFunction scoreMethod,
			Field field, boolean isForVariants) throws ConfigurationException {
		RandomScoreFunctionBuilder randomFunction = ScoreFunctionBuilders.randomFunction()
				.setWeight(scoreMethod.getWeight());
		Object randomSeed = scoreMethod.getOptions().get(ScoreOption.random_seed);
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
		String scriptCode = scoringFunction.getOptions().get(ScoreOption.script_code);
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

	public CombineFunction getBoostMode() {
		return CombineFunction.fromString(scoreConf.getBoostMode().name().toUpperCase());
	}

	public ScoreMode getScoreMode() {
		return ScoreMode.fromString(scoreConf.getScoreMode().name().toUpperCase());
	}
}
