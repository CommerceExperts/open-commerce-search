# SearchResult
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**inputURI** | **URI** | The URI that was used to get that result view. May be used to generate breadcrumbs. | [optional] [default to null]
**meta** | **Map** |  | [optional] [default to null]
**slices** | [**List**](SearchResultSlice.md) | The result may consist of several slices, for example if a search request couldn&#39;t be answered matching all words (e.g. \&quot;striped nike shirt\&quot;) then one slice could be the result for one part of the query (e.g. \&quot;striped shirt\&quot;) and the other could be for another part of the query (e.g. \&quot;nike shirt\&quot;). This can also be used to deliver some special advertised products or to split the result in different ranked slices (e.g. the first 3 results are ranked by popularity, the next 3 are sorted by price and the rest is ranked by &#39;default&#39; relevance). Each slice contains the {@link SearchQuery} that represent that exact slice. At least 1 slice should be expected. If there is no slice, no results were found. | [optional] [default to null]
**sortOptions** | [**List**](Sorting.md) |  | [optional] [default to null]
**tookInMillis** | **Long** | amount of time the internal search needed to compute that result | [optional] [default to null]

[[Back to Model list]](../index.md#documentation-for-models) [[Back to API list]](../index.md#documentation-for-api-endpoints) [[Back to README]](../index.md)

