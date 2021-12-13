package de.cxp.ocs.model.params;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(allOf = { ProductSet.class })
public class StaticProductSet extends ProductSet {

	public String type = "static";

	@NonNull
	public String[] ids;

	@NonNull
	public String name;

	@Override
	public int getSize() {
		return ids.length;
	}

}
