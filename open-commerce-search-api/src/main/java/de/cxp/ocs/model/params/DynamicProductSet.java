package de.cxp.ocs.model.params;

import java.util.Map;

import javax.validation.constraints.Min;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A product set defined by dynamic search query text, filters and optional
 * sorting order.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(allOf = { ProductSet.class })
public class DynamicProductSet extends ProductSet {

	public final String type = "dynamic";

	public String name;

	public String query;

	public String sort;

	public Map<String, String> filters;

	/**
	 * The maximum amount of products to pick into the set. These will be the
	 * first products provided by the other parameters.
	 */
	@Min(1)
	public int limit = 3;

	@Override
	public int getSize() {
		return limit;
	}

}
