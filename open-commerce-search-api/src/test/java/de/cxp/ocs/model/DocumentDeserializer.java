package de.cxp.ocs.model;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ValueNode;

import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Category;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;

public class DocumentDeserializer extends JsonDeserializer<Document> {

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
		if (categoriesNode != null && categoriesNode.isArray() && ((ArrayNode)categoriesNode).size() > 0) {
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
				Object extractedValue = extractValue((ValueNode) valueNode);
				data.put(field, extractedValue);
			}
			else if (valueNode.isArray()) {
				List<Object> values = new ArrayList<>(valueNode.size());
				// used to ensure the same value type is used in the array
				Class<?> type = null;
				for (int i = 0; i < valueNode.size(); i++) {
					JsonNode arrayValueNode = (JsonNode) valueNode.get(i);
					if (arrayValueNode.isValueNode()) {
						Object extractedValue = extractValue((ValueNode) arrayValueNode);
						if (type == null) {
							type = extractedValue.getClass();
						}
						else if (!type.isAssignableFrom(extractedValue.getClass())) {
							continue;
						}
						values.add(extractedValue);
					}
					else {
						// log error? throw exception? ignore?!
					}
				}
				if (type != null && values.size() > 0) {
					data.put(field, toBestMatchingArray(values, type));
				}
			}
			else if (valueNode.isObject()) {
				extractAttribute((JsonNode) valueNode).ifPresent(attr -> data.put(field, attr));
			}
		}
		return data;
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
		JsonNode idNode = treeNode.get("id");
		JsonNode labelNode = treeNode.get("label");
		JsonNode codeNode = treeNode.get("code");
		JsonNode valueNode = treeNode.get("value");

		if (labelNode == null || labelNode.isMissingNode() || !labelNode.isValueNode()
				|| valueNode == null || valueNode.isMissingNode() || !valueNode.isValueNode()
				|| (idNode != null && !idNode.isValueNode())
				|| (codeNode != null && !codeNode.isValueNode())) {
			return Optional.empty();
		}

		return Optional.of(
				new Attribute(
						idNode == null ? null : idNode.textValue(),
						labelNode.textValue(),
						codeNode == null ? null : codeNode.textValue(),
						valueNode.textValue()));
	}

	private static Object extractValue(ValueNode valueNode) {
		if (valueNode == null || valueNode.isMissingNode() || valueNode instanceof NullNode) {
			return null;
		}
		if (valueNode instanceof NumericNode) {
			if (((NumericNode) valueNode).isFloatingPointNumber()) {
				return ((NumericNode) valueNode).asDouble();
			}
			else if (((NumericNode) valueNode).canConvertToInt()) {
				return ((NumericNode) valueNode).asInt();
			}
			else {
				return ((NumericNode) valueNode).asLong();
			}
		}
		return ((JsonNode) valueNode).asText();
	}

}
