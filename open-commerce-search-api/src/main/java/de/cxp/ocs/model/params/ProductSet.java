package de.cxp.ocs.model.params;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Schema(
		discriminatorProperty = "type",
		discriminatorMapping = {
				@DiscriminatorMapping(value = "static", schema = StaticProductSet.class),
				@DiscriminatorMapping(value = "dynamic", schema = DynamicProductSet.class),
		})
public abstract class ProductSet {

	/**
	 * If set to true, the result of that product set will be separated from the main result.
	 * However facets won't be separated and still part of the main result's slice.
	 */
	@Getter
	@Setter
	public boolean asSeparateSlice = false;

	@Getter
	@Setter
	public String variantBoostTerms = null;

	public abstract String getType();

	/**
	 * @return Name of the product set, at which the results can be identified
	 *         in the returned slices.
	 */
	public abstract String getName();

	public abstract int getSize();

}
