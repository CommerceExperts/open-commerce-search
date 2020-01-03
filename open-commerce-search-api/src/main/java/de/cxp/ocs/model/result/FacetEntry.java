package de.cxp.ocs.model.result;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Schema(
		discriminatorProperty = "_type",
		discriminatorMapping = {
				@DiscriminatorMapping(value = "hierarchical", schema = HierarchialFacetEntry.class),
				@DiscriminatorMapping(value = "simple", schema = FacetEntry.class)
		})
@Data
@AllArgsConstructor
public class FacetEntry {

	public final String	_type	= "simple";
	String	key;
	long	docCount;

}
