package de.cxp.ocs.indexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.indexer.model.DataItem;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.indexer.model.MasterItem;
import de.cxp.ocs.indexer.model.VariantItem;
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * converts {@link Document} / {@link Product} objects into
 * {@link DataItem}
 */
@RequiredArgsConstructor
public class IndexItemConverter {

	@NonNull
	private final Map<String, Field> fields;

	public IndexableItem toIndexableItem(Document doc) {
		// TODO: validate document (e.g. require IDs etc.)
		IndexableItem indexableItem;
		if (doc instanceof Product) {
			indexableItem = toMasterVariantItem((Product) doc);
		}
		else {
			indexableItem = new IndexableItem(doc.getId());
		}

		extractSourceValues(doc, indexableItem);

		return indexableItem;
	}

	private MasterItem toMasterVariantItem(Product doc) {
		MasterItem targetMaster = new MasterItem(doc.getId());

		for (final Document variantDocument : doc.getVariants()) {
			final VariantItem targetVariant = new VariantItem(targetMaster);
			extractSourceValues(variantDocument, targetVariant);
			targetMaster.getVariants().add(targetVariant);
		}
		return targetMaster;
	}

	private void extractSourceValues(Document sourceDoc, final DataItem targetItem) {
		boolean isVariant = (targetItem instanceof VariantItem);
		final Map<String, Object> sourceData = sourceDoc.getData();
		for (final Field field : fields.values()) {
			if ((field.isVariantLevel() && !isVariant) || (field.isMasterLevel() && isVariant)) {
				continue;
			}

			switch (field.getType()) {
				case id:
					// no ID handling here
					// TODO: use that field information to retrieve an ID from
					// the data in case the source document itself has not
					// explicit ID set.
					break;
				case category:
					if (targetItem instanceof IndexableItem) {
						extractCategoryPaths(field, sourceDoc, targetItem);
					}
					break;
				default:
					List<String> sourceValues = new ArrayList<>(1);
					Object value = sourceData.get(field.getName());
					if (value != null) sourceValues.add(value.toString());

					if (field.getSourceNames() != null) {

						for (int i = 0; i < field.getSourceNames().size(); i++) {
							Object sourceValue = sourceData.get(field.getSourceNames().get(i));
							if (sourceValue != null) {
								sourceValues.add(sourceValue.toString());
							}
						}
						if (sourceValues.size() > 0) {
							value = sourceValues;
						}
					}

					if (!sourceValues.isEmpty()) {
						targetItem.setValue(field, sourceValues.size() == 1 ? sourceValues.get(0) : sourceValues);
					}
			}
		}
	}

	private void extractCategoryPaths(final Field field, Document sourceDoc, final DataItem targetItem) {
		Set<String> categoryPaths = ((IndexableItem) targetItem).getCategories();

		// handle special category field
		if (sourceDoc.getCategories() != null) {
			List<Category[]> sourceCategories = sourceDoc.getCategories();
			for (Category[] categoryPath : sourceCategories) {
				categoryPaths.add(toCategoryPathString(categoryPath));
			}
		}
		// fallback: try to get categories from the standard data
		// fields
		else if (field.getSourceNames() != null && !field.getSourceNames().isEmpty()) {
			for (int i = 0; i < field.getSourceNames().size(); i++) {
				Object sourceValue = sourceDoc.getData().get(field.getSourceNames().get(i));
				if (sourceValue != null) {
					if (sourceValue instanceof List<?>) {
						((List<?>) sourceValue).forEach(o -> categoryPaths.add(o.toString()));
					}
					else if (sourceValue instanceof String[]) {
						for (String s : (String[]) sourceValue) {
							categoryPaths.add(s);
						}
					}
					else {
						categoryPaths.add(sourceValue.toString());
					}
				}
			}
		}
	}

	private String toCategoryPathString(Category[] categories) {
		StringBuilder categoryPath = new StringBuilder();
		for (Category c : categories) {
			if (categoryPath.length() > 0) categoryPath.append('/');
			categoryPath.append(c.getName().replace("/", "%2F"));
			if (c.getId() != null) categoryPath.append(':').append(c.getId());
		}
		return categoryPath.toString();
	}
}
