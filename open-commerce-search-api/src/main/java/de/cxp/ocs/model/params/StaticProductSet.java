package de.cxp.ocs.model.params;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
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
