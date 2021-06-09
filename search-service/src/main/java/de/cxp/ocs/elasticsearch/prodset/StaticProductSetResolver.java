package de.cxp.ocs.elasticsearch.prodset;

import java.util.stream.StreamSupport;

import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.StaticProductSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StaticProductSetResolver implements ProductSetResolver {

	@Override
	public StaticProductSet resolve(final ProductSet productSet, int extraBuffer, Searcher searcher, SearchContext searchContext) {
		StaticProductSet staticSet = (StaticProductSet) productSet;
		IdsQueryBuilder addIds = QueryBuilders.idsQuery().addIds(staticSet.getIds());
		try {
			SearchResponse searchResponse = searcher.executeSearchRequest(SearchSourceBuilder.searchSource().query(addIds).size(productSet.getSize()));
			if (searchResponse.getHits().getTotalHits().value == 0) {
				staticSet.setIds(new String[0]);
			}
			else if (searchResponse.getHits().getTotalHits().value < productSet.getSize()
					&& searchResponse.getHits().getTotalHits().relation.equals(TotalHits.Relation.EQUAL_TO)) {
				staticSet.setIds((String[]) StreamSupport.stream(searchResponse.getHits().spliterator(), false)
						.map(hit -> hit.getId())
						.toArray());
			}
		}
		catch (Exception e) {
			log.error("{} while verifying productSet ids. Won't verify.", e.getMessage());
		}
		return staticSet;
	}

	@Override
	public boolean runAsync() {
		return true;
	}

}
