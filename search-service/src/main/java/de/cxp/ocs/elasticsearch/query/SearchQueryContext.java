package de.cxp.ocs.elasticsearch.query;

import java.util.ArrayList;
import java.util.List;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.sort.SortBuilder;

import de.cxp.ocs.elasticsearch.prodset.HeroProductsQuery;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import lombok.Data;

/**
 * All parts that build together the final search query.
 * 
 * @author Rudolf Batt
 */
@Data
public class SearchQueryContext {

	public TextMatchQuery<QueryBuilder> text;

	public FilterContext filters;

	public ScoringContext scoring;

	public List<SortBuilder<?>> variantSortings = new ArrayList<>();

	public HeroProductsQuery heroProducts;

}
