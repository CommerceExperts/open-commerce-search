package de.cxp.ocs.model.params;

import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FilteredSearchQuery extends SearchQuery {

	public Map<String, String>	filters;
}
