# SearchResultSlice
## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**facets** | [**List**](Facet.md) | If facets are part of this slice, they are placed here. By default only one slice SHOULD contain facets. | [optional] [default to null]
**hits** | [**List**](ResultHit.md) | the list of actual hits for that result view. | [optional] [default to null]
**label** | **String** | An identifier for that result slice. Can be used to differentiate different slices. Values depend on the implementation. | [optional] [default to null]
**matchCount** | **Long** | the absolute number of matches in this result. | [optional] [default to null]
**nextLink** | **URI** | URL conform query parameters, that has to be used to get the next bunch of results. Is null if there are no more results. | [optional] [default to null]
**nextOffset** | **Long** | the offset value to use to get the next result batch | [optional] [default to null]
**resultLink** | **URI** | The query that represents exact that passed slice. If send to the engine again, that slice should be returned as main result. | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

