# IndexerApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**add**](IndexerApi.md#add) | **POST** /indexer-api/v1/full/add | 
[**cancel**](IndexerApi.md#cancel) | **POST** /indexer-api/v1/full/cancel | 
[**done**](IndexerApi.md#done) | **POST** /indexer-api/v1/full/done | 
[**startImport**](IndexerApi.md#startImport) | **GET** /indexer-api/v1/full/start/{indexName} | 


<a name="add"></a>
# **add**
> String add(BulkImportData)



    Add one or more documents to a running import session.

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **BulkImportData** | [**BulkImportData**](../Models/BulkImportData.md)| Data that contains the import session reference and one or more documents that should be added to that session. |

### Return type

**String**

### Authorization

[basic-auth](../index.md#basic-auth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: */*

<a name="cancel"></a>
# **cancel**
> cancel(ImportSession)



    Cancels the import and in case there was an index created, it will be deleted.

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ImportSession** | [**ImportSession**](../Models/ImportSession.md)|  |

### Return type

null (empty response body)

### Authorization

[basic-auth](../index.md#basic-auth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="done"></a>
# **done**
> done(ImportSession)



    Finishes the import, flushing the new index and (in case there is already an index with the initialized name) replacing the old one.

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ImportSession** | [**ImportSession**](../Models/ImportSession.md)|  |

### Return type

null (empty response body)

### Authorization

[basic-auth](../index.md#basic-auth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="startImport"></a>
# **startImport**
> ImportSession startImport(indexName, locale)



    Starts a new full import. Returns a handle containing meta data, that has to be passed to all following calls.

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **indexName** | **String**| index name, that should match the regular expression &#39;[a-z0-9_-]+&#39; | [default to null]
 **locale** | **String**| used for language dependent settings | [default to null]

### Return type

[**ImportSession**](../Models/ImportSession.md)

### Authorization

[basic-auth](../index.md#basic-auth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: */*

