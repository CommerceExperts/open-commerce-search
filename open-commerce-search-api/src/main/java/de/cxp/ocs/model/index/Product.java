package de.cxp.ocs.model.index;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Main product containing the data that is common for all variants.
 * 
 * A product may be represented in a master-variant relation ship.
 * A variant is associated to a single {@link Product}. It should
 * only contain data special to that variant. Data that is common to all
 * variants is set at master level.
 */
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
	Document[] variants;

}
