package de.cxp.ocs.model.result;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(allOf = {FacetEntry.class})
public class HierarchialFacetEntry extends FacetEntry {

	public final String type = "hierarchical";

	/**
	 * Child facet entries to that particular facet. The child facets again
	 * could be HierarchialFacetEntries.
	 */
	@Schema(description = "Child facet entries to that particular facet. The child facets again could be HierarchialFacetEntries.")
	public List<FacetEntry> children = new ArrayList<>();

	public String path;

	public HierarchialFacetEntry(String key, String id, long docCount, String link, boolean isSelected) {
		super(key, id, docCount, link, isSelected);
	}

	public HierarchialFacetEntry addChild(final FacetEntry child) {
		children.add(child);
		return this;
	}
}
