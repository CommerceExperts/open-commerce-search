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

	private FacetConfig defaultTermFacetConfiguration = new FacetConfig();

	private FacetConfig defaultNumberFacetConfiguration = new FacetConfig().setType(FacetType.INTERVAL.name());

	private List<FacetConfig> facets = new ArrayList<>();

	private int maxFacets = 5;

	/**
	 * A list of fine grained facet configurations. Each facet configuration
	 * controls the return value of one specific facet.
	 * Facets without configuration will be configured by default values.
	 * 
	 * @param facets
	 *        set full facets list
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
	 *        set facet limit
	 * @return self
	 */
	public FacetConfiguration setMaxFacets(int maxFacets) {
		this.maxFacets = maxFacets;
		return this;
	}

	@Deprecated
	public FacetConfiguration setDefaultFacetConfiguration(de.cxp.ocs.config.FacetConfiguration.FacetConfig defaultFacetConfiguration) {
		this.defaultTermFacetConfiguration = defaultFacetConfiguration;
		return this;
	}

	public FacetConfiguration setDefaultTermFacetConfiguration(de.cxp.ocs.config.FacetConfiguration.FacetConfig defaultTermFacetConfiguration) {
		this.defaultTermFacetConfiguration = defaultTermFacetConfiguration;
		return this;
	}

	public FacetConfiguration setDefaultNumberFacetConfiguration(de.cxp.ocs.config.FacetConfiguration.FacetConfig defaultNumberFacetConfiguration) {
		this.defaultNumberFacetConfiguration = defaultNumberFacetConfiguration;
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

		private int order = 1000;

		private ValueOrder valueOrder = ValueOrder.COUNT;

		private boolean excludeFromFacetLimit = false;

		private boolean preferVariantOnFilter = false;

		private double minFacetCoverage = 0.1;

		private int minValueCount = 2;

		private boolean isFilterSensitive = false;

		private String[] filterDependencies = new String[0];

		/**
		 * Label of that facet
		 * 
		 * @param label
		 *        label to set
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
		 *        set field name this facet relates to
		 * @return self
		 */
		public FacetConfig setSourceField(String sourceField) {
			this.sourceField = sourceField;
			return this;
		}

		/**
		 * <p>
		 * Optional type that relates to the available FacetCreators.
		 * If not set, it uses the default type of the related field.
		 * </p>
		 * From some field-types different facet types can be generated:
		 * <ul>
		 * <li>numeric fields generate "interval" facets per default, but can be
		 * set to "range"</li>
		 * <li>TODO: custom facet creators can support their own facet
		 * types</li>
		 * </ul>
		 * <p>
		 * If set to 'ignore' the facet creation is avoided, even if that facet
		 * is indexed.
		 * </p>
		 * 
		 * @param type
		 *        type of facet
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
		 *        arbitrary data map
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
		 *        this is a number
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
		 *        set true to activate
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
		 *        set true to activate
		 * @return self
		 */
		public FacetConfig setMultiSelect(boolean isMultiSelect) {
			this.isMultiSelect = isMultiSelect;
			return this;
		}

		public FacetConfig setIsMultiSelect(boolean isMultiSelect) {
			this.isMultiSelect = isMultiSelect;
			return this;
		}

		/**
		 * Set to true if the attributes for a facet can have multiple values per document, but the facet should only
		 * return the filtered one.
		 * 
		 * @param isFilterSensitive
		 * @return
		 */
		public FacetConfig setFilterSensitive(boolean isFilterSensitive) {
			this.isFilterSensitive = isFilterSensitive;
			return this;
		}

		public FacetConfig setIsFilterSensitive(boolean isFilterSensitive) {
			this.isFilterSensitive = isFilterSensitive;
			return this;
		}

		/**
		 * Optional index, to put the facets in a consistent order.
		 * 
		 * @param order
		 *        numeric value between 0 and 127
		 * @return self
		 */
		public FacetConfig setOrder(int order) {
			this.order = order;
			return this;
		}

		/**
		 * If set to true, this facet will always be shown and not removed
		 * because of facet limit.
		 * 
		 * @param excludeFromFacetLimit
		 *        set true to activate
		 * @return self
		 */
		public FacetConfig setExcludeFromFacetLimit(boolean excludeFromFacetLimit) {
			this.excludeFromFacetLimit = excludeFromFacetLimit;
			return this;
		}

		/**
		 * <p>
		 * Set to true, if variant documents should be preferred in the
		 * result in case a filter of that facet/field is used. This can only
		 * be used for facets/fields, that exist on variant level, otherwise it
		 * is ignored.
		 * </p>
		 * <p>
		 * If several facets have this flag activated, one of them must be
		 * filtered to prefer a variant. E.g. if you have different variants per
		 * "color" and "material", and you set this flag for both facets,
		 * variants will be shown if there is either a color or a material
		 * filter.
		 * </p>
		 * 
		 * @param preferVariantOnFilter
		 *        default is false. set to true to activate.
		 * @return self
		 */
		public FacetConfig setPreferVariantOnFilter(boolean preferVariantOnFilter) {
			this.preferVariantOnFilter = preferVariantOnFilter;
			return this;
		}

		/**
		 * <p>
		 * Set the order of the facet values. Defaults to COUNT which means, the
		 * value with the highest result coverage will be listed first.
		 * </p>
		 * 
		 * <p>
		 * This setting is only used for term-facets and category-facets.
		 * </p>
		 * 
		 * @param valueOrder
		 *        order of the values for that facet
		 * @return self
		 */
		public FacetConfig setValueOrder(ValueOrder valueOrder) {
			this.valueOrder = valueOrder;
			return this;
		}

		/**
		 * <p>
		 * Set the minimum ratio of the result a facet has to cover in order to be
		 * displayed.
		 * </p>
		 * <p>
		 * For example with a value of 0.2 for a "color" facet, it will only be shown,
		 * if at least 20% of the products in a result have a "color" attribute (even if
		 * all have the same color).
		 * </p>
		 * <p>
		 * Per default that value is set to 0.1.
		 * </p>
		 * 
		 * @param minFacetCoverage
		 *        value between 0 and 1, defining the ratio of a
		 *        facet's result coverate.
		 * @return self
		 */
		public FacetConfig setMinFacetCoverage(double minFacetCoverage) {
			this.minFacetCoverage = minFacetCoverage;
			return this;
		}

		/**
		 * <p>
		 * Set the minimum amount of values a facet must have in order to be displayed.
		 * </p>
		 * <p>
		 * For example with a value of 2 for a "color" facet, that facet will only be
		 * shown, if the result contains matches with at least 2 different colors, e.g.
		 * "black" and "red".
		 * </p>
		 * <p>
		 * Per default the value is 2.
		 * </p>
		 * <p>
		 * For facets with a total value count lower than this setting, this setting is
		 * automatically reduced to exactly that determined total value count.
		 * </p>
		 * 
		 * @param minValueCount
		 *        value equals or greater than 0
		 * @return self
		 */
		public FacetConfig setMinValueCount(int minValueCount) {
			this.minValueCount = minValueCount;
			return this;
		}

		/**
		 * <p>
		 * Set one or more URL-style filters of other facets that are required to make
		 * this facet visible.
		 * </p>
		 * <p>
		 * The facet with such dependencies will only be displayed if one of those
		 * filters is present. A filter-definition can also contain more than one filter
		 * dependency.
		 * </p>
		 * <p>
		 * If a facet should just generally depend on a filter, a wildcard can be used
		 * to denote that, for example "category=*" as dependency would make a facet be
		 * displayed as soon as any category filter is selected.<br>
		 * (However the wildcard is not some kind of regular expression, so it can NOT
		 * be used to express some partial matching! For example "category=F*" would be
		 * considered as dependency on exactly that filter value.)
		 * </p>
		 * <p>
		 * More examples:
		 * <ul>
		 * <li>Multiple filter dependencies:
		 * <p>
		 * <code>filterDependencies: [ "category=furniture", "category=apparel" ]</code>
		 * </p>
		 * <p>
		 * With this setting, a facet is only shown, if the category "furniture" OR
		 * "apparel" is selected.
		 * </p>
		 * </li>
		 * <li>Combined filter dependency:
		 * <p>
		 * <code>filterDependencies: [ "category=furniture&amp;brand=mybrand" ]</code>
		 * </p>
		 * <p>
		 * With this setting, a facet is only shown, if the category "furniture" AND the
		 * brand "mybrand" are selected.
		 * </p>
		 * </li>
		 * <li>Multivalue filter dependency:
		 * <p>
		 * <code>filterDependencies: [ "color=red,black" ]</code>
		 * </p>
		 * <p>
		 * With this setting, a facet is only shown, if the colors "red" AND "black" are
		 * selected. More selected colors would not have an impact.
		 * </p>
		 * </li>
		 * <li>Dependency on path filter:
		 * <p>
		 * <code>filterDependencies: [ "category=furniture/closets" ]</code>
		 * <p>
		 * With this setting, the size facet is only shown, if the full category path
		 * "furniture/closets" is selected.
		 * </p>
		 * <p>
		 * <strong>Please note</strong> that if those categories are filtered by their
		 * ID, this dependency won't match. In such a case the IDs must be defined here.
		 * </p>
		 * </li>
		 * </ul>
		 * <p>
		 * It is only possible to defined filter parameters, not "limit", "offset", "q"
		 * or any other reserved parameter. Those will make a facet disappear completely
		 * because such filter will never be set.
		 * </p>
		 * <p>
		 * Also keep in mind, that the filters from this setting are parsed as URL query
		 * parameters, so values like '%25' are decoded accordingly. It is not required
		 * to encode everything, however it is necessary for reserved characters
		 * [<code>/,=&amp;%</code>] that appear inside values.
		 * </p>
		 * 
		 * @param filterDependencies
		 *        list of filter dependencies
		 * @return self
		 */
		public FacetConfig setFilterDependencies(String... filterDependencies) {
			this.filterDependencies = filterDependencies;
			return this;
		}

		public static enum ValueOrder {
			COUNT, ALPHANUM_ASC, ALPHANUM_DESC, HUMAN_NUMERIC_ASC, HUMAN_NUMERIC_DESC;
		}
	}
}
