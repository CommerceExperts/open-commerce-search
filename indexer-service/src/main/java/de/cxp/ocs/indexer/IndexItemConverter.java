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
			if ((isVariant && field.isVariantLevel() || !isVariant && field.isMasterLevel())) {
				Object value = sourceData.get(field.getName());

				// if there is no value by the field name but there are source
				// fields defined, check all of them until a value can be
				// extracted
				if (value == null && field.getSourceNames() != null && !field.getSourceNames().isEmpty()) {
					for (int i = 0; value == null && i < field.getSourceNames().size(); i++) {
						value = sourceData.get(field.getSourceNames().get(i));
					}
				}
				if (value != null) {
					targetItem.setValue(field, value);
				}
			}
		}
	}
}
