package de.cxp.ocs.model.index;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Main product containing the data that is common for all variants.
 * <p>
 * A product may represent a master-variant relation ship.
 * A variant is associated to a single {@link Product} and cannot have variants
 * again - those will be ignored.
 * It should only contain data special to that variant. 
 * Data that is common to all variants should be set at master level.
 */

@Schema(
		description = "Main product containing the data that is common for all variants."
				+ " A product may represent a master-variant relation ship."
				+ " A variant should be associated to a single Product and cannot have variants again - those will be ignored."
				+ " It should only contain data special to that variant. Data that is common to all variants should be set at master level.",
		allOf = {Document.class})
@Data
@NoArgsConstructor
@ToString(callSuper = true)
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class Product extends Document {

	public Product(String id) {
		super(id);
	}

	/**
	 * for products without variants, it can be null or rather us a document
	 * directly.
	 */
	@Schema(description = "for products without variants, it can be null or rather us a document directly.")
	public Document[] variants;

}
