package de.cxp.ocs.elasticsearch.query.sort;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.model.result.SortOrder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SortInstruction {

	private final Field		field;
	private final String	rawSortValue;
	private final SortOrder	sortOrder;

}
