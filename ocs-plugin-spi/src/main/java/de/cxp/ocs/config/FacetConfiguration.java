package de.cxp.ocs.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class FacetConfiguration {

	@NonNull
	private final List<FacetConfig> facets = new ArrayList<>();

	private int maxFacets = 5;

	@Data
	@NoArgsConstructor
	@RequiredArgsConstructor
	public static class FacetConfig {

		@NonNull
		private String label;

		@NonNull
		private String sourceField;

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
		 */
		private String type;

		private final Map<String, Object> metaData = new HashMap<>();

		/**
		 * Primary used for numeric facets to build according number of value
		 * ranges.
		 * Can also be used for advanced displaying term facets.
		 */
		private int optimalValueCount = 5;

		/**
		 * Set to true if all options should be shown after filtering on one of
		 * the options of the same facet.
		 */
		private boolean showUnselectedOptions = false;

		/**
		 * Set to true if it should be possible to select several different
		 * values of the same facet.
		 */
		private boolean isMultiSelect = false;

		private byte order = Byte.MAX_VALUE;

		/**
		 * if set to true, this facet will always be shown and not removed
		 * because of facet limit.
		 */
		private boolean excludeFromFacetLimit = false;

	}
}
