# SearchQuery
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **limit** | **Integer** | The amount of products to return in the result | [optional] [default to null] |
| **offset** | **Integer** | The amount of products to omit from the whole result to select the returned results. | [optional] [default to null] |
| **q** | **String** | the user query | [optional] [default to null] |
| **sort** | **String** | Full sorting parameter value. This is the name of the sorting and optionally a dash as prefix, thats means the sorting should be descending. Several sorting criterion can be defined by separating the values using comma. | [optional] [default to null] |
| **withFacets** | **Boolean** | flag to specify if facets should be returned with the requested response. Should be set to false in case only the next batch of hits is requested (e.g. for endless scrolling). | [optional] [default to null] |

[[Back to Model list]](../index.md#documentation-for-models) [[Back to API list]](../index.md#documentation-for-api-endpoints) [[Back to README]](../index.md)

