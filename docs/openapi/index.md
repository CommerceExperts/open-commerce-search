<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

| Class | Method | HTTP request | Description |
|------------ | ------------- | ------------- | -------------|
| *IndexerApi* | [**add**](Apis/IndexerApi.md#add) | **POST** /indexer-api/v1/full/add | Add one or more documents to a running import session. |
*IndexerApi* | [**cancel**](Apis/IndexerApi.md#cancel) | **POST** /indexer-api/v1/full/cancel | Cancels the import and in case there was an index created, it will be deleted. |
*IndexerApi* | [**done**](Apis/IndexerApi.md#done) | **POST** /indexer-api/v1/full/done | Finishes the import, flushing the new index and (in case there is already an index with the initialized name) replacing the old one. |
*IndexerApi* | [**startImport**](Apis/IndexerApi.md#startimport) | **GET** /indexer-api/v1/full/start/{indexName} | Starts a new full import. Returns a handle containing meta data, that has to be passed to all following calls. |
| *SearchApi* | [**arrangedSearch**](Apis/SearchApi.md#arrangedsearch) | **POST** /search-api/v1/search/arranged/{tenant} |  |
*SearchApi* | [**getDocument**](Apis/SearchApi.md#getdocument) | **GET** /search-api/v1/doc/{tenant}/{id} |  |
*SearchApi* | [**getTenants**](Apis/SearchApi.md#gettenants) | **GET** /search-api/v1/tenants |  |
*SearchApi* | [**search**](Apis/SearchApi.md#search) | **GET** /search-api/v1/search/{tenant} | Search for documents |
| *SuggestApi* | [**suggest**](Apis/SuggestApi.md#suggest) | **GET** /suggest-api/v1/{indexname}/suggest | Autocomplete the user input |
| *UpdateApi* | [**deleteDocuments**](Apis/UpdateApi.md#deletedocuments) | **DELETE** /indexer-api/v1/update/{indexName} | Delete existing document. If document does not exist, it returns code 304. |
*UpdateApi* | [**patchDocuments**](Apis/UpdateApi.md#patchdocuments) | **PATCH** /indexer-api/v1/update/{indexName} | Partial update of existing documents. If a document does not exist, no update will be performed and it gets the result status 'NOT_FOUND'. In case a document is a master product with variants, the provided master product may only contain the changed values. However if some of the variants should be updated, all data from all variant products are required, unless you have an ID data-field inside variant - then you can update single variants. Without variant ID field, the missing variants won't be there after the update! This is how single variants can be deleted. |
*UpdateApi* | [**putDocuments**](Apis/UpdateApi.md#putdocuments) | **PUT** /indexer-api/v1/update/{indexName} | Puts a document to the index. If document does not exist, it will be added, but in that case the langCode parameter is required. An existing product will be overwritten unless the parameter 'replaceExisting\" is set to \"false\". Provided document should be a complete object, partial updates should be done using the updateDocument method. |


<a name="documentation-for-models"></a>
## Documentation for Models

 - [ArrangedSearchQuery](./Models/ArrangedSearchQuery.md)
 - [Attribute](./Models/Attribute.md)
 - [BulkImportData](./Models/BulkImportData.md)
 - [Category](./Models/Category.md)
 - [Document](./Models/Document.md)
 - [Document_data_value](./Models/Document_data_value.md)
 - [DynamicProductSet](./Models/DynamicProductSet.md)
 - [Facet](./Models/Facet.md)
 - [FacetEntry](./Models/FacetEntry.md)
 - [GenericProductSet](./Models/GenericProductSet.md)
 - [HierarchialFacetEntry](./Models/HierarchialFacetEntry.md)
 - [ImportSession](./Models/ImportSession.md)
 - [IntervalFacetEntry](./Models/IntervalFacetEntry.md)
 - [Product](./Models/Product.md)
 - [ProductSet](./Models/ProductSet.md)
 - [QueryStringProductSet](./Models/QueryStringProductSet.md)
 - [RangeFacetEntry](./Models/RangeFacetEntry.md)
 - [ResultHit](./Models/ResultHit.md)
 - [SearchQuery](./Models/SearchQuery.md)
 - [SearchResult](./Models/SearchResult.md)
 - [SearchResultSlice](./Models/SearchResultSlice.md)
 - [Sorting](./Models/Sorting.md)
 - [StaticProductSet](./Models/StaticProductSet.md)
 - [Suggestion](./Models/Suggestion.md)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

<a name="basic-auth"></a>
### basic-auth

- **Type**: HTTP basic authentication

