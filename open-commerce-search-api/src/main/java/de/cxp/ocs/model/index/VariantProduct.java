package de.cxp.ocs.model.index;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * Variant product that is associated to a {@link MasterProduct}. It should only
 * contain data special to that variant. Common data is set at master level.
 * 
 * Data MUST NOT contain entries with the name "id", "price", "title",
 * "categoryPaths" or "variants"! Such data fields will be ignored.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class VariantProduct extends Document {

	public VariantProduct(String id) {
		super(id);
	}

	Double price;

	/**
	 * Optional list of non-default prices. This can be reduced prices, prices
	 * with other tax rules or prices for different customer groups.
	 */
	Map<String, Double> prices = new HashMap<>();

}
