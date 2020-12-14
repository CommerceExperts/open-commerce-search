package de.cxp.ocs.indexer;

import java.util.Map.Entry;
import java.util.function.Predicate;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldConfiguration;
import de.cxp.ocs.indexer.model.DataItem;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.indexer.model.MasterItem;
import de.cxp.ocs.indexer.model.VariantItem;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import lombok.extern.slf4j.Slf4j;

/**
 * converts {@link Document} / {@link Product} objects into
 * {@link DataItem}
 */
@Slf4j
public class IndexItemConverter {

	private FieldConfigIndex fieldConfigIndex;

	/**
	 * Constructor of the converter that prepares the given field configurations
	 * for converting Documents into {@link IndexableItem}.
	 * 
	 * @param fieldConfiguration
	 */
	public IndexItemConverter(FieldConfiguration fieldConfiguration) {
		fieldConfigIndex = new FieldConfigIndex(fieldConfiguration);
	}

	/**
	 * Converts a Document coming in via the REST API into the Indexable Item
	 * for Elasticsearch.
	 * 
	 * @param doc
	 * @return
	 */
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
		final boolean isVariant = (targetItem instanceof VariantItem);
		Predicate<Field> fieldAtCorrectDocLevelPredicate = isVariant ? this::isFieldAtVariantLevel : this::isFieldAtMasterLevel;

		if (sourceDoc.getData() != null) {
			for (Entry<String, Object> dataField : sourceDoc.getData().entrySet()) {
				fieldConfigIndex.getMatchingFields(dataField.getKey(), dataField.getValue())
						.stream()
						.filter(fieldAtCorrectDocLevelPredicate)
						.forEach(field -> targetItem.setValue(field, dataField.getValue()));
			}
		}

		if (sourceDoc.getAttributes() != null) {
			for (Attribute attribute : sourceDoc.getAttributes()) {
				fieldConfigIndex.getMatchingFields(attribute.getLabel(), attribute)
						.stream()
						.filter(fieldAtCorrectDocLevelPredicate)
						.forEach(field -> targetItem.setValue(field, attribute));
			}
		}

		fieldConfigIndex.getCategoryField().ifPresent(f -> targetItem.setValue(f, sourceDoc.getCategories()));
	}

	private boolean isFieldAtVariantLevel(Field field) {
		return (field.isBothLevel() || field.isVariantLevel());
	}

	private boolean isFieldAtMasterLevel(Field field) {
		return (field.isBothLevel() || field.isMasterLevel());
	}
}
