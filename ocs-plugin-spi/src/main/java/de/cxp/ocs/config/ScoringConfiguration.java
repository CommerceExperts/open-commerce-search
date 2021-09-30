package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Configuration that influences how the result hits are scored.
 * 
 * <a href=
 * "https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html">Checkout
 * the Elasticsearch scoring function documentation</a>. This is basically a one
 * to one mapping of it.
 */
@Getter // write setters with java-doc!
@NoArgsConstructor
public class ScoringConfiguration {

	private ScoreMode	scoreMode	= ScoreMode.AVG;
	private BoostMode	boostMode	= BoostMode.AVG;

	private List<ScoringFunction> scoreFunctions = new ArrayList<>();

	/**
	 * The score_mode specifies how the computed scores are combined.
	 * Default: AVG
	 * 
	 * @param scoreMode
	 * @return self
	 */
	public ScoringConfiguration setScoreMode(ScoreMode scoreMode) {
		this.scoreMode = scoreMode;
		return this;
	}

	/**
	 * The boost_mode specified, how the score is combined with the score of the
	 * query.
	 * Default: AVG
	 * 
	 * @param boostMode
	 * @return self
	 */
	public ScoringConfiguration setBoostMode(BoostMode boostMode) {
		this.boostMode = boostMode;
		return this;
	}

	/**
	 * Set the list of scoring rules.
	 * 
	 * @param scoreFunctions
	 * @return self
	 */
	public ScoringConfiguration setScoreFunctions(List<ScoringFunction> scoreFunctions) {
		this.scoreFunctions = scoreFunctions;
		return this;
	}

	/**
	 * Specific configuration for each scoring rule.
	 */
	@Getter // write setters with java-doc!
	@NoArgsConstructor
	public static class ScoringFunction {

		private String field;

		private ScoreType type = ScoreType.FIELD_VALUE_FACTOR;

		private float weight = 1f;

		private Map<ScoreOption, String> options = new HashMap<>();

		/**
		 * Data field that should be used for that scoring rule.
		 * 
		 * @param field
		 * @return self
		 */
		public ScoringFunction setField(String field) {
			this.field = field;
			return this;
		}

		/**
		 * Set how scoring works for that field.
		 * 
		 * @param type
		 * @return self
		 */
		public ScoringFunction setType(ScoreType type) {
			this.type = type;
			return this;
		}

		/**
		 * Set the weight that is multiplied with the scoring function result.
		 * 
		 * @param weight
		 * @return self
		 */
		public ScoringFunction setWeight(float weight) {
			this.weight = weight;
			return this;
		}

		/**
		 * Set additional options required for the according scoring type.
		 * 
		 * @param options
		 * @return self
		 */
		public ScoringFunction setOptions(Map<ScoreOption, String> options) {
			this.options = options;
			return this;
		}
	}
}
