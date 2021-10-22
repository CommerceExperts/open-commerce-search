# Documentation for Open Commerce Search API

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*IndexerApi* | [**add**](Apis/IndexerApi.html#add) | **POST** /indexer-api/v1/full/add | Add one or more documents to a running import session.
*IndexerApi* | [**cancel**](Apis/IndexerApi.html#cancel) | **POST** /indexer-api/v1/full/cancel | Cancels the import and in case there was an index created, it will be deleted.
*IndexerApi* | [**done**](Apis/IndexerApi.html#done) | **POST** /indexer-api/v1/full/done | Finishes the import, flushing the new index and (in case there is already an index with the initialized name) replacing the old one.
*IndexerApi* | [**startImport**](Apis/IndexerApi.html#startimport) | **GET** /indexer-api/v1/full/start/{indexName} | Starts a new full import. Returns a handle containing meta data, that has to be passed to all following calls.
*SearchApi* | [**arrangedSearch**](Apis/SearchApi.html#arrangedsearch) | **POST** /search-api/v1/search/arranged/{tenant} | 
*SearchApi* | [**getDocument**](Apis/SearchApi.html#getdocument) | **GET** /search-api/v1/doc/{tenant}/{id} | 
*SearchApi* | [**getTenants**](Apis/SearchApi.html#gettenants) | **GET** /search-api/v1/tenants | 
*SearchApi* | [**search**](Apis/SearchApi.html#search) | **GET** /search-api/v1/search/{tenant} | Search for documents
*SuggestApi* | [**suggest**](Apis/SuggestApi.html#suggest) | **GET** /suggest-api/v1/{indexname}/suggest | Autocomplete the user input
*UpdateApi* | [**deleteDocuments**](Apis/UpdateApi.html#deletedocuments) | **DELETE** /indexer-api/v1/update/{indexName} | Delete existing document. If document does not exist, it returns code 304.
*UpdateApi* | [**patchDocuments**](Apis/UpdateApi.html#patchdocuments) | **PATCH** /indexer-api/v1/update/{indexName} | Partial update of existing documents. If a document does not exist, no update will be performed and it gets the result status 'NOT_FOUND'. In case a document is a master product with variants, the provided master product may only contain the changed values. However if some of the variants should be updated, all data from all variant products are required, unless you have an ID data-field inside variant - then you can update single variants. Without variant ID field, the missing variants won't be there after the update! This is how single variants can be deleted.
*UpdateApi* | [**putDocuments**](Apis/UpdateApi.html#putdocuments) | **PUT** /indexer-api/v1/update/{indexName} | Puts a document to the index. If document does not exist, it will be added. An existing product will be overwritten unless the parameter 'replaceExisting\" is set to \"false\". Provided document should be a complete object, partial updates should be  done using the updateDocument method.


<a name="documentation-for-models"></a>
## Documentation for Models

 - [ArrangedSearchQuery](./Models/ArrangedSearchQuery.html)
 - [Attribute](./Models/Attribute.html)
 - [BulkImportData](./Models/BulkImportData.html)
 - [Category](./Models/Category.html)
 - [Document](./Models/Document.html)
 - [DynamicProductSet](./Models/DynamicProductSet.html)
 - [DynamicProductSet_allOf](./Models/DynamicProductSet_allOf.html)
 - [Facet](./Models/Facet.html)
 - [FacetEntry](./Models/FacetEntry.html)
 - [HierarchialFacetEntry](./Models/HierarchialFacetEntry.html)
 - [HierarchialFacetEntry_allOf](./Models/HierarchialFacetEntry_allOf.html)
 - [ImportSession](./Models/ImportSession.html)
 - [IntervalFacetEntry](./Models/IntervalFacetEntry.html)
 - [IntervalFacetEntry_allOf](./Models/IntervalFacetEntry_allOf.html)
 - [Product](./Models/Product.html)
 - [ProductSet](./Models/ProductSet.html)
 - [Product_allOf](./Models/Product_allOf.html)
 - [RangeFacetEntry](./Models/RangeFacetEntry.html)
 - [RangeFacetEntry_allOf](./Models/RangeFacetEntry_allOf.html)
 - [ResultHit](./Models/ResultHit.html)
 - [SearchQuery](./Models/SearchQuery.html)
 - [SearchResult](./Models/SearchResult.html)
 - [SearchResultSlice](./Models/SearchResultSlice.html)
 - [Sorting](./Models/Sorting.html)
 - [StaticProductSet](./Models/StaticProductSet.html)
 - [StaticProductSet_allOf](./Models/StaticProductSet_allOf.html)
 - [Suggestion](./Models/Suggestion.html)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

<a name="basic-auth"></a>
### basic-auth

- **Type**: HTTP basic authentication

