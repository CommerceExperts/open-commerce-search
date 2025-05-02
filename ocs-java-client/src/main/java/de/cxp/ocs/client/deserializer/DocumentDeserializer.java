package de.cxp.ocs.client.deserializer;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;

public class DocumentDeserializer extends JsonDeserializer<Document> {

	private final static Logger log = Logger.getLogger(DocumentDeserializer.class.getCanonicalName());

	@Override
	public Document deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		TreeNode docNode = p.readValueAsTree();

		TreeNode variantsNode = docNode.get("variants");
		if (variantsNode != null && variantsNode.isArray()) {
			return p.getCodec().treeToValue(docNode, Product.class);
		}

		return extractDocument(new Document(), docNode, p);
	}

	static Document extractDocument(Document doc, TreeNode docNode, JsonParser p) throws JsonProcessingException {
		JsonNode idNode = (JsonNode) docNode.get("id");
		if (idNode != null && idNode.isValueNode()) doc.setId(idNode.asText());

		JsonNode dataNode = (JsonNode) docNode.get("data");
		if (dataNode != null && dataNode.isObject()) {
			doc.setData(extractValidData(dataNode));
		}

		JsonNode attributesNode = (JsonNode) docNode.get("attributes");
		if (attributesNode != null && attributesNode.isArray()) {
			doc.setAttributes(p.getCodec().treeToValue(attributesNode, Attribute[].class));
		}

		JsonNode categoriesNode = (JsonNode) docNode.get("categories");
		if (categoriesNode != null && categoriesNode.isArray() && !((ArrayNode) categoriesNode).isEmpty()) {
			doc.setCategories(Arrays.asList(p.getCodec().treeToValue(categoriesNode, Category[][].class)));
		}

		return doc;
	}

	static Map<String, Object> extractValidData(JsonNode dataNode) {
		Map<String, Object> data = new HashMap<>(dataNode.size());
		Iterator<String> fieldNameIterator = dataNode.fieldNames();
		while (fieldNameIterator.hasNext()) {
			String field = fieldNameIterator.next();
			TreeNode valueNode = dataNode.get(field);

			if (valueNode == null || valueNode.isMissingNode()) continue;

			if (valueNode.isValueNode()) {
				Object extractedValue = extractValue((ValueNode) valueNode, null);
				data.put(field, extractedValue);
			}
			else if (valueNode.isArray()) {
				Object extractedArrayValue = extractArrayValue((ArrayNode) valueNode, 0);

				if (extractedArrayValue != null) {
					data.put(field, extractedArrayValue);
				}
			}
			else if (valueNode.isObject()) {
				extractAttribute((JsonNode) valueNode).ifPresent(attr -> data.put(field, attr));
			}
		}
		return data;
	}

	private static Object extractArrayValue(ArrayNode valueNode, int extractionLevel) {
		if (extractionLevel > 1) throw new IllegalArgumentException("document with array nested too deep!");

		Object extractedArrayValue = null;
		List<Object> values = new ArrayList<>(valueNode.size());
		// used to ensure the same value type is used in the array
		Class<?> type = null;
		for (int i = 0; i < valueNode.size(); i++) {
			JsonNode arrayValueNode = valueNode.get(i);
			if (arrayValueNode.isValueNode()) {
				Object extractedValue = extractValue((ValueNode) arrayValueNode, type);
				if (type == null) {
					type = extractedValue.getClass();
				}
				else if (!type.isAssignableFrom(extractedValue.getClass())) {
					log.finest("discarding value of type " + extractedValue.getClass().getCanonicalName() + "; expected constistent array type " + type.getCanonicalName());
					continue;
				}
				values.add(extractedValue);
			}
			else if (arrayValueNode.isObject() && looksLikeCategory((ObjectNode) arrayValueNode) && (type == null || type.equals(Category.class))) {
				type = Category.class;
				ObjectNode objNode = ((ObjectNode) arrayValueNode);
				// id is optional and can be null
				Object id = objNode.has("id") && objNode.get("id").isValueNode() ? extractValue((ValueNode) objNode.get("id"), String.class) : null;
				// the 'name' property is already checked inside the 'looksLikeCategory' method
				Object name = extractValue((ValueNode) objNode.get("name"), String.class);
				values.add(new Category(id == null ? null : id.toString(), name.toString()));
			}
			else if (arrayValueNode.isArray()) {
				Object nestedArray = extractArrayValue((ArrayNode) arrayValueNode, extractionLevel + 1);
				if (nestedArray != null && (type == null || type.isArray())) {
					if (type == null) {
						type = nestedArray.getClass();
						values.add(nestedArray);
					}
					else if (type.isAssignableFrom(nestedArray.getClass())) {
						values.add(nestedArray);
					}
					else {
						log.finest("discarding array value of type " + nestedArray.getClass().getCanonicalName() + "; expected constistent array type " + type.getCanonicalName());
						continue;
					}
				}
			}
			else {
				log.finest("discard value of unsupported type " + arrayValueNode.getClass().getCanonicalName());
			}
		}

		if (type != null && !values.isEmpty()) {
			extractedArrayValue = toBestMatchingArray(values, type);
		}
		return extractedArrayValue;
	}

	private static boolean looksLikeCategory(ObjectNode objNode) {
		return objNode.has("name") && objNode.get("name").isValueNode();
	}

	private static Object toBestMatchingArray(List<Object> values, Class<?> valueType) {
		if (valueType.equals(Integer.class)) {
			int[] primitiveValues = new int[values.size()];
			for (int i = 0; i < values.size(); i++) {
				primitiveValues[i] = (int) values.get(i);
			}
			return primitiveValues;
		}
		else if (valueType.equals(Double.class)) {
			double[] primitiveValues = new double[values.size()];
			for (int i = 0; i < values.size(); i++) {
				primitiveValues[i] = (double) values.get(i);
			}
			return primitiveValues;
		}
		else if (valueType.equals(Long.class)) {
			long[] primitiveValues = new long[values.size()];
			for (int i = 0; i < values.size(); i++) {
				primitiveValues[i] = (long) values.get(i);
			}
			return primitiveValues;
		}
		else {
			return values.toArray((Object[]) Array.newInstance(valueType, values.size()));
		}
	}

	private static Optional<Attribute> extractAttribute(JsonNode treeNode) {
		JsonNode nameNode = treeNode.get("name");
		JsonNode codeNode = treeNode.get("code");
		JsonNode valueNode = treeNode.get("value");

		if (nameNode == null || nameNode.isMissingNode() || !nameNode.isValueNode()
				|| valueNode == null || valueNode.isMissingNode() || !valueNode.isValueNode()
				|| (codeNode != null && !codeNode.isValueNode())) {
			return Optional.empty();
		}

		return Optional.of(
				new Attribute(
						nameNode.textValue(),
						codeNode == null ? null : codeNode.textValue(),
						valueNode.textValue()));
	}

	private static Object extractValue(ValueNode valueNode, Class<?> preferedType) {
		if (valueNode == null || valueNode.isMissingNode() || valueNode instanceof NullNode) {
			return null;
		}
		if (valueNode instanceof NumericNode && (preferedType == null || Number.class.isAssignableFrom(preferedType))) {
			if (((NumericNode) valueNode).isFloatingPointNumber() && (preferedType == null || preferedType.isAssignableFrom(Double.class))) {
				return ((NumericNode) valueNode).asDouble();
			}
			else if (((NumericNode) valueNode).canConvertToInt() && (preferedType == null || preferedType.isAssignableFrom(Integer.class))) {
				return ((NumericNode) valueNode).asInt();
			}
			else {
				return ((NumericNode) valueNode).asLong();
			}
		}
		return ((JsonNode) valueNode).asText();
	}

}
