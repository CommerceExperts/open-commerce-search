package de.cxp.ocs.indexer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DocumentPatcher {

	public static Set<String> getRequiredFieldsForMerge(Document doc, @NonNull FieldConfigIndex fieldConfIndex) {
		Set<String> fetchFields = new HashSet<>();
		for (Entry<String, Object> dataEntry : doc.getData().entrySet()) {
			Set<Field> matchingFields = fieldConfIndex.getMatchingFields(dataEntry.getKey());
			for (Field f : matchingFields) {
				if (FieldType.COMBI.equals(f.getType())) {
					fetchFields.addAll(f.getSourceNames());
				}
				if (f.getUsage().contains(FieldUsage.FACET)) {
					if (FieldType.NUMBER.equals(f.getType())) {
						fetchFields.add(FieldConstants.NUMBER_FACET_DATA);
					}
					else if (FieldType.CATEGORY.equals(f.getType())) {
						fetchFields.add(FieldConstants.PATH_FACET_DATA);
					}
					else {
						fetchFields.add(FieldConstants.TERM_FACET_DATA);
					}
				}
			}
		}

		if (doc instanceof Product && ((Product) doc).getVariants().length > 0) {
			fetchFields.add(FieldConstants.VARIANTS);
		}

		return fetchFields;
	}

	private static Optional<Field> getVariantIdField(FieldConfigIndex fieldConfIndex) {
		return fieldConfIndex.getFieldsByType(FieldType.ID).values().stream()
				.filter(Field::isVariantLevel)
				.filter(f -> f.getUsage().contains(FieldUsage.RESULT) || f.getUsage().contains(FieldUsage.SEARCH) || f.getUsage().contains(FieldUsage.SORT))
				.findFirst();
	}

	public static Document patchDocument(Document patchDocument, Document indexedDocument, @NonNull FieldConfigIndex fieldConfIndex) {
		// remove combi fields, because they are artificially created
		// and will be created again
		removeCombinedFields(indexedDocument, fieldConfIndex);

		if (patchDocument.attributes != null) {
			Set<String> patchedAttributeNames = patchDocument.attributes.stream().map(Attribute::getName).collect(Collectors.toSet());
			// first remove all data with same name as the patched attributes
			for (String patchedAttrName : patchedAttributeNames) {
				indexedDocument.data.remove(patchedAttrName);
				indexedDocument.attributes.removeIf(a -> a.getName().equals(patchedAttrName));
			}
			patchDocument.attributes.forEach(indexedDocument::addAttribute);
		}

		// if there are patched categories, overwrite the old ones completely
		if (patchDocument.categories != null) {
			indexedDocument.setCategories(patchDocument.categories);
		}

		// overwrite data
		indexedDocument.data.putAll(patchDocument.data);

		if (patchDocument instanceof Product && ((Product) patchDocument).variants != null && ((Product) patchDocument).variants.length > 0) {
			indexedDocument = patchVariants(patchDocument, indexedDocument, fieldConfIndex);
		}

		return indexedDocument;
	}

	private static void removeCombinedFields(final Document indexedDocument, final FieldConfigIndex fieldConfIndex) {
		for (Field field : fieldConfIndex.getFields().values()) {
			if (FieldType.COMBI.equals(field.getType())) {
				indexedDocument.data.remove(field.getName());
			}
			else if (field.getSourceNames().size() > 1) {
				// this could be done in a single if,
				// but that would be hardly readable
				Optional<String> missingSourceField = field.getSourceNames().stream()
						.filter(sourceName -> !indexedDocument.data.containsKey(sourceName))
						.findFirst();
				if (!missingSourceField.isPresent()) {
					indexedDocument.data.remove(field.getName());
				}
			}
		}
	}

	private static Document patchVariants(Document patchDocument, Document indexedDocument, FieldConfigIndex fieldConfIndex) {
		Product _indexedDocument;
		if (!(indexedDocument instanceof Product)) {
			_indexedDocument = new Product(indexedDocument.id);
			_indexedDocument.attributes = indexedDocument.attributes;
			_indexedDocument.categories = indexedDocument.categories;
			_indexedDocument.data = indexedDocument.data;
			indexedDocument = _indexedDocument;
		}
		else {
			_indexedDocument = (Product) indexedDocument;
		}

		if (_indexedDocument.variants == null) {
			_indexedDocument.variants = ((Product) patchDocument).variants;
		}
		else {
			Optional<Field> variantIdField = getVariantIdField(fieldConfIndex);
			if (variantIdField.isPresent()) {
				patchVariantsById(patchDocument, fieldConfIndex, _indexedDocument, variantIdField);
			}
			else {
				// variants have no ID, so overwrite existing ones
				// completely with patch variants
				_indexedDocument.variants = ((Product) patchDocument).variants;
			}
		}
		return _indexedDocument;
	}

	private static void patchVariantsById(Document patchDocument, FieldConfigIndex fieldConfIndex, Product _indexedDocument, Optional<Field> variantIdField) {
		Field varIdField = variantIdField.get();
		Map<String, Document> patchVariants = new HashMap<>();
		for (Document patchVariant : ((Product) patchDocument).variants) {
			String varId = getVariantId(patchVariant, varIdField);
			if (varId == null) {
				log.warn("Received document {} with variants, but variant ID field {} is not set! Will ignore variant.",
						patchDocument.getId(), varIdField.getName());
			}
			else {
				patchVariants.put(varId, patchVariant);
			}
		}

		List<Document> allVariants = new ArrayList<>(_indexedDocument.variants.length);
		for (Document existingVariant : _indexedDocument.variants) {
			Object varId = getVariantId(existingVariant, varIdField);
			if (varId == null) {
				log.warn("Found indexed variant for document {} without ID field '{}' specified! Won't update it.",
						existingVariant.getId(), varIdField.getName());
			}
			else {
				Document patchVariant = patchVariants.remove(varId);
				if (patchVariant != null) {
					existingVariant = patchDocument(patchVariant, existingVariant, fieldConfIndex);
				}
				allVariants.add(existingVariant);
			}
		}
		// add all variants that didn't exist yet
		allVariants.addAll(patchVariants.values());
		_indexedDocument.variants = allVariants.toArray(new Document[allVariants.size()]);
	}

	private static String getVariantId(Document patchVariant, Field varIdField) {
		if (patchVariant.id != null && !"null".equals(patchVariant.id)) return patchVariant.id;
		Object potentialId = patchVariant.getData().get(varIdField.getName());
		if (potentialId != null) return potentialId.toString();

		for (String sourceFieldName : varIdField.getSourceNames()) {
			potentialId = patchVariant.getData().get(sourceFieldName);
			if (potentialId != null) return potentialId.toString();
		}
		return null;
	}
}
