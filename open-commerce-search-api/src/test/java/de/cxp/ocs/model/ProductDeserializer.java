package de.cxp.ocs.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;

public class ProductDeserializer extends JsonDeserializer<Product> {

	@Override
	public Product deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		TreeNode docNode = p.readValueAsTree();

		Product product = new Product();

		TreeNode variantsNode = docNode.get("variants");
		if (variantsNode != null && variantsNode.isArray()) {
			List<Document> variants = new ArrayList<>();
			for (int i = 0; i < ((ArrayNode) variantsNode).size(); i++) {
				if (variantsNode.get(i) != null
						&& variantsNode.get(i).isObject()
						&& variantsNode.get(i).get("data") != null) {
					Document extractedDocument = DocumentDeserializer.extractDocument(variantsNode.get(i));
					variants.add(extractedDocument);
				}
			}
			product.setVariants(variants.toArray(new Document[variants.size()]));
		}

		TreeNode idNode = docNode.get("id");
		if (idNode != null && idNode.isValueNode()) product.setId(((JsonNode) idNode).asText());

		JsonNode dataNode = (JsonNode) docNode.get("data");
		if (dataNode != null && dataNode.isObject()) {
			product.setData(DocumentDeserializer.extractValidData(dataNode));
		}

		return product;
	}

}
