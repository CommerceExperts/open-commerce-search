package de.cxp.ocs.model.params;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
		discriminatorProperty = "type",
		discriminatorMapping = {
				@DiscriminatorMapping(value = "static", schema = StaticProductSet.class),
				@DiscriminatorMapping(value = "dynamic", schema = DynamicProductSet.class),
		})
public abstract class ProductSet {

	public abstract String getType();

	/**
	 * @return Name of the product set, at which the results can be identified
	 *         in the returned slices.
	 */
	public abstract String getName();

	public abstract int getSize();

}
