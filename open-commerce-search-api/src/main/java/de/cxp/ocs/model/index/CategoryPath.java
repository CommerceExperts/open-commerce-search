package de.cxp.ocs.model.index;

import lombok.Data;
import lombok.NonNull;

@Data
public class CategoryPath {

	public CategoryPath(Category[] categories) {
		if (categories == null || categories.length == 0) {
			throw new IllegalArgumentException("at least one category level required!");
		}
		this.categories = categories;
	}

	@NonNull
	final Category[] categories;

	/**
	 * Creates a simple path using categories without IDs.
	 * 
	 * @param firstLevelCategory
	 * @param categoryNames
	 * @return
	 */
	public static CategoryPath simplePath(@NonNull String firstLevelCategory, String... categoryNames) {
		Category[] categories = new Category[categoryNames.length + 1];
		categories[0] = new Category(firstLevelCategory);
		for (int i = 0; i < categoryNames.length; i++) {
			categories[i + 1] = new Category(categoryNames[i]);
		}
		return new CategoryPath(categories);
	}
}
