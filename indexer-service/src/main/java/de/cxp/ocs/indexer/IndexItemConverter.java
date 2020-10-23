package de.cxp.ocs.indexer;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.indexer.model.DataItem;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.indexer.model.MasterItem;
import de.cxp.ocs.indexer.model.VariantItem;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * converts {@link Document} / {@link Product} objects into
 * {@link DataItem}
 */
@Slf4j
public class IndexItemConverter {

	@NonNull
	private final Map<String, Field>	fields;
	private final Optional<Field>		categoryField;

	public IndexItemConverter(Map<String, Field> fields) {
		this.fields = fields;
		categoryField = determineDefaultCategoryField(fields);
	}

	private Optional<Field> determineDefaultCategoryField(Map<String, Field> fields) {
		Map<String, Field> categoryFields = fields.values().stream()
				.filter(v -> FieldType.category.equals(v.getType()))
				.collect(Collectors.toMap(Field::getName, v -> v));
		// if there are several fields of type category, try to determine which
		// one is the most suitable for Document::categories
		if (categoryFields.size() > 1) {
			// best case: one of the fields has one of these preferred names:
			for (String preferedCatFieldName : new String[] { "categories", "category" }) {
				if (categoryFields.containsKey(preferedCatFieldName)) {
					categoryFields.entrySet().removeIf(e -> !e.getKey().equals(preferedCatFieldName));
					log.warn("Multiple category fields defined! Will index Document::categories into field with prefered name '{}'!", preferedCatFieldName);
					break;
				}
			}

			// alternative case: if one field has no "source field names",
			// it should be the categories field.
			if (categoryFields.size() > 1) {
				categoryFields.entrySet().removeIf(e -> e.getValue().getSourceNames().size() > 0);
				if (categoryFields.isEmpty()) {
					log.warn("Multiple category fields defined, but none with one of the prefered names (categories/category) or without source names found!"
							+ " Won't index Document::categories data!");
				}
			}

			// last case: we don't know which one is the most suitable one.
			// Don't index categories at all.
			if (categoryFields.size() > 1) {
				categoryFields.clear();
				log.warn("Multiple category fields defined, but none has unique characteristic! Won't index Document::categories data!");
			}
		}
		return categoryFields.isEmpty() ? Optional.empty() : Optional.of(categoryFields.values().iterator().next());
	}

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

			Object value = sourceData.get(field.getName());
			targetItem.setValue(field, value);
			
			if (field.getSourceNames() != null) {
				for (int i = 0; i < field.getSourceNames().size(); i++) {
					targetItem.setValue(field, sourceData.get(field.getSourceNames().get(i)));
				}
			}
		}
		
		categoryField.ifPresent(f -> targetItem.setValue(f, sourceDoc.getCategories()));
	}

}
