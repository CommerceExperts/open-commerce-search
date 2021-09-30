package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter // write setters with java-doc!
@NoArgsConstructor
public class FacetConfiguration {

	private List<FacetConfig> facets = new ArrayList<>();

	private int maxFacets = 5;

	/**
	 * A list of fine grained facet configurations. Each facet configuration
	 * controls the return value of one specific facet.
	 * Facets without configuration will be configured by default values.
	 * 
	 * @param facets
	 * @return self
	 */
	public FacetConfiguration setFacets(@NonNull List<FacetConfig> facets) {
		this.facets = facets;
		return this;
	}

	/**
	 * Limit the amount of all facets returned for a result. Facets that have
	 * the property 'excludeFromFacetLimit' enabled, won't be considered for
	 * that limit.
	 * 
	 * @param maxFacets
	 * @return self
	 */
	public FacetConfiguration setMaxFacets(int maxFacets) {
		this.maxFacets = maxFacets;
		return this;
	}

	@Getter // write setters with java-doc!
	@NoArgsConstructor
	@RequiredArgsConstructor
	public static class FacetConfig {

		@NonNull
		private String label;

		@NonNull
		private String sourceField;

		private String type;

		private Map<String, Object> metaData = new HashMap<>();

		private int optimalValueCount = 5;

		private boolean showUnselectedOptions = false;

		private boolean isMultiSelect = false;

		private byte order = Byte.MAX_VALUE;

		private boolean excludeFromFacetLimit = false;

		/**
		 * Label of that facet
		 * 
		 * @param label
		 * @return self
		 */
		public FacetConfig setLabel(String label) {
			this.label = label;
			return this;
		}

		/**
		 * Required: Set name of data field that is configured with these
		 * config.
		 * 
		 * @param sourceField
		 * @return self
		 */
		public FacetConfig setSourceField(String sourceField) {
			this.sourceField = sourceField;
			return this;
		}

		/**
		 * Optional type that relates to the available FacetCreators.
		 * If not set, it uses the default type of the related field.
		 * From some field-types different facet types can be generated:
		 * <ul>
		 * <li>numeric fields generate "interval" facets per default, but can be
		 * set to "range"</li>
		 * <li>TODO: custom facet creators can support their own facet
		 * types</li>
		 * </ul>
		 * 
		 * @param type
		 * @return self
		 */
		public FacetConfig setType(String type) {
			this.type = type;
			return this;
		}

		/**
		 * Optional map that is returned with that facet. Can be used for
		 * additional data you need with the facet for visualizing.
		 * 
		 * @param metaData
		 * @return self
		 */
		public FacetConfig setMetaData(Map<String, Object> metaData) {
			this.metaData = metaData;
			return this;
		}

		/**
		 * Primary used for numeric facets to build according number of value
		 * ranges.
		 * Can also be used for advanced displaying term facets.
		 * 
		 * @param optimalValueCount
		 * @return self
		 */
		public FacetConfig setOptimalValueCount(int optimalValueCount) {
			this.optimalValueCount = optimalValueCount;
			return this;
		}

		/**
		 * Set to true if all options should be shown after filtering on one of
		 * the options of the same facet.
		 * 
		 * @param showUnselectedOptions
		 * @return self
		 */
		public FacetConfig setShowUnselectedOptions(boolean showUnselectedOptions) {
			this.showUnselectedOptions = showUnselectedOptions;
			return this;
		}

		/**
		 * Set to true if it should be possible to select several different
		 * values of the same facet.
		 * 
		 * @param isMultiSelect
		 * @return self
		 */
		public FacetConfig setMultiSelect(boolean isMultiSelect) {
			this.isMultiSelect = isMultiSelect;
			return this;
		}

		/**
		 * Optional index, to put the facets in a consistent order.
		 * 
		 * @param order
		 * @return self
		 */
		public FacetConfig setOrder(byte order) {
			this.order = order;
			return this;
		}

		/**
		 * If set to true, this facet will always be shown and not removed
		 * because of facet limit.
		 * 
		 * @param excludeFromFacetLimit
		 * @return self
		 */
		public FacetConfig setExcludeFromFacetLimit(boolean excludeFromFacetLimit) {
			this.excludeFromFacetLimit = excludeFromFacetLimit;
			return this;
		}

	}
}
