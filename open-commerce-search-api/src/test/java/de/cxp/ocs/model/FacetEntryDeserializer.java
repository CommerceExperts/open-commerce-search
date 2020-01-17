package de.cxp.ocs.model;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.HierarchialFacetEntry;

public class FacetEntryDeserializer extends JsonDeserializer<FacetEntry> {

	@Override
	public FacetEntry deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		TreeNode docNode = p.readValueAsTree();

		TreeNode variantsNode = docNode.get("children");
		if (variantsNode != null && variantsNode.isArray()) {
			return p.getCodec().treeToValue(docNode, HierarchialFacetEntry.class);
		}

		FacetEntry entry = new FacetEntry();
		Optional.ofNullable((JsonNode) docNode.get("docCount")).map(JsonNode::asLong).ifPresent(entry::setDocCount);
		Optional.ofNullable((JsonNode) docNode.get("key")).map(JsonNode::textValue).ifPresent(entry::setKey);
		Optional.ofNullable((JsonNode) docNode.get("link")).map(JsonNode::textValue).ifPresent(entry::setLink);
		return entry;
	}


}
