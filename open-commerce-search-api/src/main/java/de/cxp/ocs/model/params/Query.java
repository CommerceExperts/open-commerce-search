package de.cxp.ocs.model.params;

import java.util.ArrayList;
import java.util.List;

import de.cxp.ocs.util.ResultFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema
@Data
public class Query {

	public String userQuery;

	public List<String> filters = new ArrayList<>();

	public Query withFilter(ResultFilter filter) {
		filters.add(filter.getStringRepresentation());
		return this;
	}
}
