package de.cxp.ocs.model.params;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Getter
@EqualsAndHashCode(callSuper = false)
@Schema(allOf = { ProductSet.class }, description = "A non-specific product set usable for custom product set resolvers.")
public class GenericProductSet extends ProductSet {

	public final String	type	= "generic";
	private String		name;
	private int			size	= 0;

	private Map<String, String> parameters;
}
