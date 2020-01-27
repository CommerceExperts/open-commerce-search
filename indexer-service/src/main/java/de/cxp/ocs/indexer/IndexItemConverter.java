package de.cxp.ocs.indexer;

import java.util.Map;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.indexer.model.DataItem;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.indexer.model.MasterItem;
import de.cxp.ocs.indexer.model.VariantItem;
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

		extractSourceValues(doc.getData(), indexableItem);

		return indexableItem;
	}

	private MasterItem toMasterVariantItem(Product doc) {
		MasterItem targetMaster = new MasterItem(doc.getId());

		for (final Document variantProduct : doc.getVariants()) {
			final Map<String, Object> sourceVariantData = variantProduct.getData();
			final VariantItem targetVariant = new VariantItem(targetMaster);
			extractSourceValues(sourceVariantData, targetVariant);
			targetMaster.getVariants().add(targetVariant);
		}
		return targetMaster;
	}

	private void extractSourceValues(final Map<String, Object> sourceData, final DataItem targetItem) {
		boolean isVariant = (targetItem instanceof VariantItem);
		for (final Field field : fields.values()) {
			if ((isVariant && field.isVariantLevel() || !isVariant && field.isMasterLevel())) {
				Object value = sourceData.get(field.getName());
				if (value != null) {
					targetItem.setValue(field, value);
				}
			}
		}
	}
}
