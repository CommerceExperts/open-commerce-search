package de.cxp.ocs.model.params;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A product set defined by a simple query string query, fields with corresponding weights, optional filters and
 * sorting order.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(allOf = {ProductSet.class})
public class QueryStringProductSet extends ProductSet {

    public final String type = "querystring";

    public String name;

    public String query;

    public String sort;

    public Map<String, String> filters;

    public Map<String, Float> fieldWeights;

    /**
     * The maximum amount of products to pick into the set. These will be the
     * first products provided by the other parameters.
     */
	@Schema(description = "The amount of products to pick from that query-string product set", minimum = "1")
    public int limit = 3;

    @Override
    public int getSize() {
        return limit;
    }
}
