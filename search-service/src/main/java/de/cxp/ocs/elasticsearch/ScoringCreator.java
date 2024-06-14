package de.cxp.ocs.elasticsearch;

import static de.cxp.ocs.util.Util.tryToParseAsNumber;

import java.util.*;

import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction.Modifier;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery.ScoreMode;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RankFeatureQueryBuilder;
import org.elasticsearch.index.query.RankFeatureQueryBuilders;
import org.elasticsearch.index.query.functionscore.*;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder.FilterFunctionBuilder;
import org.elasticsearch.script.Script;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.config.*;
import de.cxp.ocs.config.ScoringConfiguration.ScoringFunction;
import de.cxp.ocs.elasticsearch.query.ScoringContext;
import de.cxp.ocs.util.ConfigurationException;
import de.cxp.ocs.util.InternalSearchParams;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScoringCreator {

	private final ScoringConfiguration	scoreConf;
	private final List<ScoringFunction>	scoreFunctions;
	private final Map<String, Field>	scoreFields;

	private final static EnumSet<ScoreType> typesRequireField = EnumSet.of(ScoreType.RANDOM_SCORE, ScoreType.FIELD_VALUE_FACTOR, ScoreType.RANK_FEATURE,
			ScoreType.DECAY_EXP, ScoreType.DECAY_GAUSS, ScoreType.DECAY_LINEAR);

	public ScoringCreator(SearchContext context) {
		scoreConf = context.config.getScoring();
		Map<String, Field> tempScoreFields = context.getFieldConfigIndex().getFieldsByUsage(FieldUsage.SCORE);
		scoreFields = Collections.unmodifiableMap(tempScoreFields);
		// copy into array, so that we can remove invalid score definitions
		scoreFunctions = extractValidScoreFunctions(scoreConf.getScoreFunctions());
	}

	private List<ScoringFunction> extractValidScoreFunctions(List<ScoringFunction> scoringFunctions) {
		List<ScoringFunction> validFunctions = new ArrayList<>();
		for (ScoringFunction sf : scoringFunctions) {
			boolean isFieldRequired = typesRequireField.contains(sf.getType());
			Optional<Field> relatedField = Optional.ofNullable(sf.getField()).map(scoreFields::get);
			if (isFieldRequired && relatedField.isEmpty()) {
				if (sf.getField() != null) {
					log.warn("Field {} for scoring does not exist. Will ignore scoring function of type {}", sf.getField(), sf.getType());
				}
				else {
					log.warn("Scoring function of type {} requires field, but non given", sf.getType());
				}
			}
			else {
				validFunctions.add(sf);
			}
		}
		return validFunctions;
	}

	public ScoringContext getScoringContext(InternalSearchParams parameters) {
		ScoringContext scoringContext = new ScoringContext();
		scoringContext.setBoostMode(CombineFunction.fromString(scoreConf.getBoostMode().name().toUpperCase()));
		scoringContext.setScoreMode(ScoreMode.fromString(scoreConf.getScoreMode().name().toUpperCase()));

		Iterator<ScoringFunction> scoreFunctionIterator = scoreFunctions.iterator();
		while (scoreFunctionIterator.hasNext()) {
			ScoringFunction scoringFunction = scoreFunctionIterator.next();
			boolean isFieldRequired = typesRequireField.contains(scoringFunction.getType());
			Optional<Field> relatedField = Optional.ofNullable(scoringFunction.getField()).map(scoreFields::get);

			boolean isMasterScoringField = relatedField.map(Field::isMasterLevel).orElse(false);
			boolean isVariantScoringField = relatedField.map(Field::isVariantLevel).orElse(false);
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

			try {
				if (isMasterScoringField || !isFieldRequired) applyFunction(scoringContext, scoringFunction, parameters, false);
				if (useForVariants) applyFunction(scoringContext, scoringFunction, parameters, true);
			}
			catch (ConfigurationException configException) {
				log.error("Applying scoring function failed: {}! Ignoring.", configException.getMessage());
			}
		}
		return scoringContext;
	}

	private void applyFunction(ScoringContext scoringContext, ScoringFunction scoringFunction, InternalSearchParams parameters, boolean useForVariants) throws ConfigurationException {
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
			case RANK_FEATURE:
				if (useForVariants) log.warn("rank-feature not supported for variant-level (on field {})", scoringFunction.getField());
				else buildRankFeatureQueries(scoringFunction, scoreFields.get(scoringFunction.getField()), parameters, scoringContext);
				break;
			default:
				buildFieldBasedScoreFunction(scoringFunction, scoreFields.get(scoringFunction.getField()), useForVariants)
						.ifPresent(f -> scoringContext.addScoringFunction(new FilterFunctionBuilder(f), useForVariants));
		}
	}

	private void buildRankFeatureQueries(ScoringFunction scoringFunction, Field field, InternalSearchParams parameters, ScoringContext scoringContext) {
		String function = scoringFunction.getOptions().getOrDefault(ScoreOption.MODIFIER, "saturation").toLowerCase();
		String fieldName;
		var opts = scoringFunction.getOptions();
		String dynamicParam = opts.get(ScoreOption.DYNAMIC_PARAM);
		if (dynamicParam != null) {
			String dynamicParamValue = parameters.getValueOf(dynamicParam);
			if (dynamicParamValue == null) return;
			fieldName = this.getFullName(field, false) + "." + dynamicParamValue;
		}
		else {
			fieldName = this.getFullName(field, false);
		}

		RankFeatureQueryBuilder rankFeatureQuery;
		switch (function) {
			case "linear":
				rankFeatureQuery = RankFeatureQueryBuilders.linear(fieldName);
				break;
			case "log":
				rankFeatureQuery = RankFeatureQueryBuilders.log(fieldName, tryToParseAsNumber(opts.get(ScoreOption.FACTOR)).orElse(1).floatValue());
				break;
			case "sigmoid":
				rankFeatureQuery = RankFeatureQueryBuilders.sigmoid(fieldName,
						tryToParseAsNumber(opts.get(ScoreOption.PIVOT)).orElse(0.5).floatValue(),
						tryToParseAsNumber(opts.get(ScoreOption.EXPONENT)).orElse(0.6).floatValue());
				break;
			case "saturation":
				rankFeatureQuery = tryToParseAsNumber(opts.get(ScoreOption.PIVOT))
						.map(pivot -> RankFeatureQueryBuilders.saturation(fieldName, pivot.floatValue()))
						.orElseGet(() -> RankFeatureQueryBuilders.saturation(fieldName));
				break;
			default:
				log.warn("rank_feature score configuration for field {} has invalid function: '{}'. Using 'saturation' instead.", field.getName(), function);
				rankFeatureQuery = RankFeatureQueryBuilders.saturation(fieldName);
		}

		tryToParseAsNumber(opts.get(ScoreOption.BOOST)).ifPresent(boost -> rankFeatureQuery.boost(boost.floatValue()));

		scoringContext.addBoostingQuery(rankFeatureQuery);

		// the missing parameter is used to apply a default score
		// for all documents that don't match the rank feature query
		tryToParseAsNumber(opts.get(ScoreOption.MISSING))
				.filter(defaultScore -> defaultScore.floatValue() > 0f)
				.ifPresent(defaultScore -> {
					scoringContext.addBoostingQuery(
							QueryBuilders.constantScoreQuery(QueryBuilders.boolQuery().mustNot(rankFeatureQuery))
									.boost(defaultScore.floatValue()));
				});
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
