package de.cxp.ocs.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.jackson.JsonComponent;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;

import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;

@JsonComponent
public class ProductDeserializer extends JsonDeserializer<Product> {

	@Override
	public Product deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		TreeNode docNode = p.readValueAsTree();

		Product product = new Product();
		DocumentDeserializer.extractDocument(product, docNode, p);

		TreeNode variantsNode = docNode.get("variants");
		if (variantsNode != null && variantsNode.isArray()) {
			List<Document> variants = new ArrayList<>();
			for (int i = 0; i < ((ArrayNode) variantsNode).size(); i++) {
				if (variantsNode.get(i) != null
						&& variantsNode.get(i).isObject()
						&& variantsNode.get(i).get("data") != null) {
					Document extractedDocument = DocumentDeserializer.extractDocument(new Document(), variantsNode.get(i), p);
					variants.add(extractedDocument);
				}
			}
			product.setVariants(variants.toArray(new Document[variants.size()]));
		}

		return product;
	}

}
