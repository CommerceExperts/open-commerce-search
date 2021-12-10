package de.cxp.ocs.model.params;

import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class FilteredSearchQuery extends SearchQuery {

	public Map<String, String>	filters;
}
