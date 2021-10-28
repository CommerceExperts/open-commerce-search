# UpdateApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**deleteDocuments**](UpdateApi.md#deleteDocuments) | **DELETE** /indexer-api/v1/update/{indexName} | 
[**patchDocuments**](UpdateApi.md#patchDocuments) | **PATCH** /indexer-api/v1/update/{indexName} | 
[**putDocuments**](UpdateApi.md#putDocuments) | **PUT** /indexer-api/v1/update/{indexName} | 


<a name="deleteDocuments"></a>
# **deleteDocuments**
> deleteDocuments(indexName, id\[\])



    Delete existing document. If document does not exist, it returns code 304.

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **indexName** | **String**|  | [default to null]
 **id\[\]** | [**List**](../Models/String.md)|  | [optional] [default to null]

### Return type

null (empty response body)

### Authorization

[basic-auth](../README.md#basic-auth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="patchDocuments"></a>
# **patchDocuments**
> patchDocuments(indexName)



    Partial update of existing documents. If a document does not exist, no update will be performed and it gets the result status &#39;NOT_FOUND&#39;. In case a document is a master product with variants, the provided master product may only contain the changed values. However if some of the variants should be updated, all data from all variant products are required, unless you have an ID data-field inside variant - then you can update single variants. Without variant ID field, the missing variants won&#39;t be there after the update! This is how single variants can be deleted.

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **indexName** | **String**|  | [default to null]

### Return type

null (empty response body)

### Authorization

[basic-auth](../README.md#basic-auth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="putDocuments"></a>
# **putDocuments**
> putDocuments(indexName, replaceExisting)



    Puts a document to the index. If document does not exist, it will be added. An existing product will be overwritten unless the parameter &#39;replaceExisting\&quot; is set to \&quot;false\&quot;. Provided document should be a complete object, partial updates should be  done using the updateDocument method.

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **indexName** | **String**|  | [default to null]
 **replaceExisting** | **Boolean**| set to false to avoid overriding a document with that ID. Defaults to &#39;true&#39; | [optional] [default to null]

### Return type

null (empty response body)

### Authorization

[basic-auth](../README.md#basic-auth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

