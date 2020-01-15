package de.cxp.ocs.model.params;

import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@NoArgsConstructor
@Accessors(chain = true)
@Data
public class SearchQuery {

	public String userQuery;

	/**
	 * Any other parameters are used as filters. They are validated according to
	 * the actual data and the configuration.
	 * 
	 * Each filter can have multiple values, separated by comma. Commas inside
	 * the values have to be double-URL encoded.
	 * Depending on the configured backend type these values are used
	 * differently.
	 * 
	 * Examples:
	 * 
	 * brand=adidas
	 * 
	 * brand=adidas,nike (=> products from adidas OR nike are shown)
	 * 
	 * category=men,shoes,sneaker (=> if category would be configured as path,
	 * these values are used for hierarchical filtering)
	 * 
	 * price=10,99.99 (=> if price is configured as numeric field, these values
	 * are
	 * used as range filters)
	 * 
	 * color=red,black (=> if that field is configured to be used for "exclusive
	 * filtering" only products would be shown that are available in red AND
	 * black)
	 * 
	 * optional for the future also negations could be supported, e.g.
	 * color=red,!black
	 */
	public Map<String, String> filters;

	/**
	 * example:
	 * sort=price
	 * sort=-price (=> descendending)
	 * sort=price,-name (=> price asc and name descending)
	 */
	public String sort;

	public int limit = 12;

	public int offset = 0;

	/**
	 * flag to specify if facets are necessary. Should be set to false in case
	 * only the next batch of hits is requests (e.g. for endless scrolling).
	 */
	public boolean withFacets = true;


}
