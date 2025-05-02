package de.cxp.ocs.client.deserializer;

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
import de.cxp.ocs.model.result.IntervalFacetEntry;
import de.cxp.ocs.model.result.RangeFacetEntry;

public class FacetEntryDeserializer extends JsonDeserializer<FacetEntry> {

	@Override
	public FacetEntry deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		TreeNode docNode = p.readValueAsTree();

		TreeNode childrenNode = docNode.get("children");
		if (childrenNode != null && childrenNode.isArray()) {
			return p.getCodec().treeToValue(docNode, HierarchialFacetEntry.class);
		}

		String type = ((JsonNode) docNode.get("type")).textValue();
		FacetEntry entry;
		if ("interval".equals(type)) {
			entry = new IntervalFacetEntry(
					parseOptionalNumber((JsonNode) docNode.get("lowerBound")),
					parseOptionalNumber((JsonNode) docNode.get("upperBound")),
					0, "", false);
		}
		else if ("range".equals(type)) {
			entry = new RangeFacetEntry(
					parseOptionalNumber((JsonNode) docNode.get("lowerBound")),
					parseOptionalNumber((JsonNode) docNode.get("upperBound")),
					0, "", false);
			Number selectedMin = parseOptionalNumber((JsonNode) docNode.get("selectedMin"));
			Number selectedMax = parseOptionalNumber((JsonNode) docNode.get("selectedMax"));
			if (selectedMin != null && selectedMax != null) {
				((RangeFacetEntry) entry).setSelectedMin(selectedMin);
				((RangeFacetEntry) entry).setSelectedMax(selectedMax);
			}
		}
		else {
			entry = new FacetEntry();
		}

		Optional.ofNullable((JsonNode) docNode.get("id")).map(JsonNode::textValue).ifPresent(entry::setId);
		Optional.ofNullable((JsonNode) docNode.get("docCount")).map(JsonNode::asLong).ifPresent(entry::setDocCount);
		Optional.ofNullable((JsonNode) docNode.get("key")).map(JsonNode::textValue).ifPresent(entry::setKey);
		Optional.ofNullable((JsonNode) docNode.get("link")).map(JsonNode::textValue).ifPresent(entry::setLink);
		Optional.ofNullable((JsonNode) docNode.get("selected")).map(JsonNode::asBoolean).ifPresent(entry::setSelected);
		return entry;
	}

	private Number parseOptionalNumber(JsonNode jsonNode) {
		if (jsonNode != null && jsonNode.isNumber())
			return jsonNode.numberValue();
		else
			return null;
	}

}
