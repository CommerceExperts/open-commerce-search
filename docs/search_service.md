# Search Service

## API Overview

The basic search API is so simple, that it's more complicated to get the right stuff from the according [Open API Spec](generated_api_doc).
The search-endpoint expects the tenant name (which normally is the the index name - more about that later) as part of the path, for example `/search-api/v1/search/my_tenant`.
Sending a GET request without any parameters will already return a result that matches all hits, from which the first 12 are returned. The result also contains the most important facets with its filters. 
Each filter item has the relevant parameters to filter accordingly. These parameters depend on your data, because they use the data field name as parameter name and the filter value as parameter value.

For a search query the well known parameter `q=` is used. Any text can go here. Depending on the configurable query-processing logic, there might be one or more result slices. (See below for details)

For page controlling the parameter `limit` and `offset` are used.

In case some specific products should be placed into the result, the arranged search endpoint has to be used. It adds the ability to built curated search results. For more details about that, have a look at the [Open API Spec](generated_api_doc.md).

### Tenant vs Index

At the indexer you will always create one index inside Elasticsearch. This index has a certain name pattern, but will be aliased with your custom index name, so you can access it by that name at the search service.

Additionaly the search service allows several different configurations for the same index. They are referenced by a "tenant name". You can also think of that as different "views" on the index.

So for example your index `my_products` can be referenced by the tenant `my_shop`, `my_mobile_shop` and `my_app`. Each of those tenants can have different settings for query-processing and search, facets and even other customizations. As the example indicates, you can use that to limit facets or results in a different way. 
You can also use that to run A/B tests for different query-processing configuration.

### Result Slices

Normally you will always get one single result, but there are usecases, where multiple slices come in handy. So it might be possible, that several slices are returned.
It is not strictly defined though, if only one or all of them can have facets or not.

To distinguish the results, the slices can be labeled. The search service will label the main result with the label "main". That result also contains the according facets.

In case an arranged search is done, the arranged results will be part of the first slice(s), but the facets of the main result will cover these arranged results as well. This also makes clear, that the slices should be processed in the order of their appearance. 

Here are some more ideas for some usecases that may be implemented with custom code:

- A multi term search phrase that leads to no results could be split into different term combination, each resulting into a different slice. In such a case it also would make sense to have facets with every slice.
- A relaxed query could add some related products to the result as a secondary slice.


## Internal Architecture

Similar to the Indexer Service, we have a standard RESTful web application based on Spring Boot. The SearchController defines the endpoints and the parameter mapping. 
The search process is centralized at the `Searcher` class. This part describes the details about that search process.

### Configuration

For every single tenant, a separate configuration is loaded and assigned to a separate `Searcher` instance. This happens at the controller.

The internal configuration is split into two parts: the tenant specific "search configuration" and the index specific "field configuration".
- The search configuration is loaded trough the `SearchConfigurationProvider` that uses Spring-Boot configuration by default. It can be customized, so you can provide the configuration controlled by your own backend. It is also possible, to enable Spring Cloud to extract the configuration as well. 
- The "field configuration" is fetched from the according Elasticsearch index.

Normally the internal configuration is fetched once and cached "for ever". Only under these conditions, the configuration is reloaded:
- The actual Elasticsearch index name changed (so a reindexation happened)
- If a tenant is not requested for a certain time (10 minutes at the moment), it will be unloaded and a new request to that tenant will load the configuration again.
- A "flushConfiguration" request forces a reload.

In case a tenant is requested, where the index does not exist, the failure is cached for 5 minutes, to avoid unnecessary query processing. (This might be removed again)


### Query Preprocessor and Analyzer

Before a "user query" (the words that someone typed into the search bar) is transformed into an Elasticsearch query, the user query can be preprocessed by a customization. This is where external spell correction or any other kind of query transformation can take place.

That resulting "search query" (the text that is put into the search engine) is analyzed and split into separate "words", for example the default `WhitespaceAnalyzer` just splits the search query by whitespace into words. The recommended `QuerqyQueryExpander` also adds synonyms to the single words.

The idea is to get some basic knowledge about the query, so decisions can be made about which and how the Elasticsearch query is built. This gets relevant in the next section.

Also spell correction and query expansion is easier, because a single word can get a "sibling" word that is searched as potential replacement. For example the user query "red shrt" can become "red (shrt OR shirt OR short)" to handle several potential corrections.


### Elasticsearch-Query Building

The Elasticsearch Query is a mighty domain specific query, that describes how the complete result should be calculated.
I contains the following building blocks:
- [the actual search query](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html) that includes the filters as well
- [pagination parameters](https://www.elastic.co/guide/en/elasticsearch/reference/current/paginate-search-results.html)
- [sorting parameters](https://www.elastic.co/guide/en/elasticsearch/reference/current/sort-search-results.html)
- [the aggregation instructions](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations.html), that produce the data necessary to build the proper filter facets
- [scoring instructions](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html)
- [includes and excludes](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-fields.html#source-filtering) of data fields for the result hits
- optional [custom rescorer definitions](https://www.elastic.co/guide/en/elasticsearch/reference/master/filter-search-results.html#rescore)

At the `Searcher` the build of that query is split into according subroutines. 
These subroutines care about their query part and the according extraction of the desired data from the result.
For example there is a `FacetConfigurationApplier` that generates the "aggregation" query part of the Elasticsearch query and then extracts the facets from the "aggregations" at the Elasticsearch result.


### Query Relaxation

The most important reason for that partial Query-Building approach is the reuse of the search-query. 
This comes handy when we try several search-query approaches, called "Query Relaxation".
"Query Relaxation" means that for a single API request several query strategies can be used to get the final result.

The idea is to use more accurate query strategies at the beginning. 
If such an accurate query does not match a single document, another more sloppy query strategy can be used to increase the likeliness of results.
As a simple example you could use an `AND` search in the first step and an `OR` search afterwards.

Of course this technique is optional and even if you have several query strategies defined, you can also define a query strategy to accept no results and thus be the last one in that chain.
This mostly makes sense in case you have a special query for number search, which should be very precise. If no document matches the requested number, you can stop searching.

All this is configurable at the tenant specific search configuration using `QueryConfiguration` definitions.
At each query configuration a strategy is defined, which basically is the name of a [ESQueryFactory](javadoc.html#apidocs/de/cxp/ocs/spi/search/ESQueryFactory.html) implementation.
Each implementations supports different settings and has different approaches. 

More details about the specific behaviour and the supported configuration options can be found at the according JavaDocs:

- [DefaultQueryFactory](javadoc.html#apidocs/de/cxp/ocs/elasticsearch/query/builder/DefaultQueryFactory.html)
- [ConfigurableQueryFactory](javadoc.html#apidocs/de/cxp/ocs/elasticsearch/query/builder/ConfigurableQueryFactory.html)
- [PredictionQueryFactory](javadoc.html#apidocs/de/cxp/ocs/elasticsearch/query/builder/PredictionQueryFactory.html)
- [NgramQueryFactory](javadoc.html#apidocs/de/cxp/ocs/elasticsearch/query/builder/NgramQueryFactory.html)

To have a better control when a strategy and when not, every query is configured with certain conditions. 
This is useful to react differently for numeric queries or have different approaches for single-term and multi-term queries.
During query processing the user-query is checked against those conditions and only the matching query strategies are used to build an Elasticsearch query.


### Facets



## 
