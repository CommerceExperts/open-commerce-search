package de.cxp.ocs.elasticsearch.model.query;

import java.util.List;

import de.cxp.ocs.elasticsearch.model.visitor.QueryTermVisitor;

public interface AnalyzedQuery {

	/**
	 * The original input terms that were used to create that query string.
	 * 
	 * @return list of unescaped terms
	 */
	List<String> getInputTerms();

	default int getTermCount() {
		return getInputTerms().size();
	}

	/**
	 * Transform into string according to the
	 * <a href=
	 * "https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax">ElasticSearch
	 * Query String syntax</a>.
	 * 
	 * TODO: change into Visitor Pattern Style: A method that accepts a visitor that is passed trough all the terms of
	 * the query. Similar to org.apache.lucene.search.QueryVisitor. This can then be used to generate the required
	 * query (LuceneQuery, structured ElasticsearchQuery, SQL Query.. =)).
	 * 
	 * @return term in query-string-query format.
	 */
	String toQueryString();

	void accept(QueryTermVisitor visitor);
}
