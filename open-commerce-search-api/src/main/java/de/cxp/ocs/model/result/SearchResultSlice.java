package de.cxp.ocs.model.result;

import java.util.List;

import de.cxp.ocs.model.params.SearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class SearchResultSlice {

	/**
	 * An identifier for that result slice. Can be used to differentiate
	 * different slices. Values depend on the implementation.
	 */
	@Schema(description = "An identifier for that result slice. Can be used to differentiate different slices. Values depend on the implementation.")
	public String label;
	
	/**
	 * the absolute number of matches in this result.
	 */
	@Schema(description = "the absolute number of matches in this result.")
	public long matchCount;

	/**
	 * the offset value to use to get the next result batch
	 */
	@Schema(description = "the offset value to use to get the next result batch")
	public long nextOffset;
	
	/**
	 * URL conform query parameters, that has to be used to get the next bunch
	 * of results. Is null if there are no more results.
	 */
	@Schema(
			format = "URI",
			description = "URL conform query parameters, that has to be used to get the next bunch of results. Is null if there are no more results.")
	public String nextLink;

	/**
	 * The query that represents exact that passed slice. If send to the engine
	 * again, that slice should be returned as main result.
	 */
	@Schema(
			description = "The query that represents exact that passed slice. If send to the engine again, that slice should be returned as main result.")
	public SearchQuery resultQuery;

	/**
	 * the list of actual hits for that result view.
	 */
	@Schema(description = "the list of actual hits for that result view.")
	public List<ResultHit> hits;

	/**
	 * If facets are part of this slice, they are placed here. By default only
	 * one slice SHOULD contain facets.
	 */
	@Schema(description = "If facets are part of this slice, they are placed here. By default only one slice SHOULD contain facets.")
	public List<Facet> facets;
	
}
