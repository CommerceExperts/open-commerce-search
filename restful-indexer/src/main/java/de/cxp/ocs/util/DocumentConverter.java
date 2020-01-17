package de.cxp.ocs.util;

import java.io.IOException;
import java.util.Map;

import org.springframework.boot.jackson.JsonComponent;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;

@JsonComponent
public class DocumentConverter {

	public static class Deserialize extends JsonDeserializer<Document> {

		@SuppressWarnings("unchecked")
		@Override
		public Document deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
			TreeNode docNode = p.readValueAsTree();

			TreeNode variantsNode = docNode.get("variants");
			if (variantsNode != null && variantsNode.isArray()) {
				return p.getCodec().treeToValue(docNode, Product.class);
			}

			Document doc = new Document();

			TreeNode idNode = docNode.get("id");
			if (idNode != null && idNode.isValueNode()) doc.setId(idNode.toString());

			TreeNode dataNode = docNode.get("data");
			if (dataNode != null && dataNode.isObject()) doc.setData(p.getCodec().treeToValue(dataNode, Map.class));

			return doc;
		}

	}
}
