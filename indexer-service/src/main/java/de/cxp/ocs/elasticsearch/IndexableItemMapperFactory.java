package de.cxp.ocs.elasticsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.cxp.ocs.indexer.model.FacetEntry;
import de.cxp.ocs.indexer.model.IndexableItem;
import de.cxp.ocs.indexer.model.VariantItem;

/**
 * Creates a {@link ObjectMapper} for {@link IndexableItem}.
 */
public class IndexableItemMapperFactory {

	public static ObjectMapper createObjectMapper() {
		final ObjectMapper objectMapper = new ObjectMapper();

		objectMapper.addMixIn(IndexableItem.class, IncludeOnlyNonEmptyMixin.class);
		objectMapper.addMixIn(FacetEntry.class, IncludeOnlyNonNullMixin.class);
		objectMapper.addMixIn(VariantItem.class, VariantItemMixin.class);

		return objectMapper;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static abstract class IncludeOnlyNonNullMixin {}
	
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	public static abstract class IncludeOnlyNonEmptyMixin {}

	@JsonIgnoreProperties("master")
	public static abstract class VariantItemMixin {}

}
