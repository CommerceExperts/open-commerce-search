package de.cxp.ocs.model.index;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * Main product containing the data that is common for all variants.
 * 
 * Data MUST NOT contain entries with the name "id", "title", "categoryPaths",
 * "prices" or "variants"! Such data fields will be ignored.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class MasterProduct extends Document {

	public MasterProduct(String id, String title) {
		super(id);
		this.title = title;
	}

	@NonNull
	String title;

	/**
	 * full category paths to this product. Normally one is enough, but multiple
	 * are allowed, even with the same parent categories.
	 */
	CategoryPath[] categoryPaths;
	// final List<CategoryPath> categoryPaths = new ArrayList<>();

	VariantProduct[] variants;
	// would that be better, since it's easier to use from a building point
	// perspective?
	// final List<VariantProduct> variants = new ArrayList<>();

}
