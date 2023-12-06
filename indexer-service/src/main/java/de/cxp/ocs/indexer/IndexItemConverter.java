package de.cxp.ocs.indexer;

import static java.util.function.Predicate.isEqual;
import static java.util.function.Predicate.not;

import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import de.cxp.ocs.conf.FieldUsageApplier;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.indexer.model.DataItem;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.indexer.model.MasterItem;
import de.cxp.ocs.indexer.model.VariantItem;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import de.cxp.ocs.spi.indexer.DocumentPostProcessor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * converts {@link Document} / {@link Product} objects into
 * {@link DataItem}
 */
@Slf4j
public class IndexItemConverter {

	@NonNull
	private final FieldConfigIndex					fieldConfigIndex;

	@NonNull
	private final List<DocumentPostProcessor>	postProcessors;

	public IndexItemConverter(FieldConfigIndex fieldConfigIndex) {
		this(fieldConfigIndex, Collections.emptyList());
	}

	public IndexItemConverter(FieldConfigIndex fieldConfigIndex, List<DocumentPostProcessor> postProcessors) {
		this.fieldConfigIndex = fieldConfigIndex;
		this.postProcessors = postProcessors;
		validate(fieldConfigIndex);

	}

	private void validate(FieldConfigIndex fieldConfigIndex2) {
		fieldConfigIndex.getFields().values().forEach(field -> {
			if (field.hasUsage(FieldUsage.FILTER) && field.hasUsage(FieldUsage.FACET)) {
				log.info("No need to configure a field with usage FILTER and FACET together as done for {}", field.getName());
			}
			if (field.hasUsage(FieldUsage.FACET) && FieldType.RAW.equals(field.getType())) {
				log.warn("Field {} of type RAW can't be used for usage=FACET. Removing usage..", field.getName());
				field.setUsage(field.getUsage().stream().filter(not(isEqual(FieldType.RAW))).collect(Collectors.toList()));
			}
		});
	}

	/**
	 * Converts a Document coming in via the REST API into the Indexable Item
	 * for Elasticsearch.
	 * 
	 * @param doc
	 *        document to be transformed
	 * @return indexable item
	 */
	public IndexableItem toIndexableItem(Document doc) {
		IndexableItem indexableItem;
		if (doc instanceof Product) {
			indexableItem = toMasterVariantItem((Product) doc);
		}
		else {
			indexableItem = new IndexableItem(doc.getId());
		}

		extractSourceValues(doc, indexableItem);

		for (DocumentPostProcessor postProcessor : postProcessors) {
			postProcessor.process(doc, indexableItem, fieldConfigIndex);
		}

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
						.forEach(field -> FieldUsageApplier.applyAll(targetItem, field, dataField.getValue()));
			}
		}

		if (sourceDoc.getAttributes() != null) {
			for (Attribute attribute : sourceDoc.getAttributes()) {
				if (attribute == null || attribute.value == null || attribute.name == null) continue;
				fieldConfigIndex.getMatchingFields(attribute.name, attribute)
						.stream()
						.filter(fieldAtCorrectDocLevelPredicate)
						.forEach(field -> FieldUsageApplier.applyAll(targetItem, field, attribute));
			}
		}

		fieldConfigIndex.getPrimaryCategoryField().ifPresent(f -> FieldUsageApplier.applyAll(targetItem, f, sourceDoc.getCategories()));
	}

	private boolean isFieldAtVariantLevel(Field field) {
		return (field.isBothLevel() || field.isVariantLevel());
	}

	private boolean isFieldAtMasterLevel(Field field) {
		return (field.isBothLevel() || field.isMasterLevel());
	}
}
