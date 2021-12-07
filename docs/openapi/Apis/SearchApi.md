# SearchApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**arrangedSearch**](SearchApi.md#arrangedSearch) | **POST** /search-api/v1/search/arranged/{tenant} | 
[**getDocument**](SearchApi.md#getDocument) | **GET** /search-api/v1/doc/{tenant}/{id} | 
[**getTenants**](SearchApi.md#getTenants) | **GET** /search-api/v1/tenants | 
[**search**](SearchApi.md#search) | **GET** /search-api/v1/search/{tenant} | Search for documents


<a name="arrangedSearch"></a>
# **arrangedSearch**
> SearchResult arrangedSearch(tenant, ArrangedSearchQuery)



### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **tenant** | **String**| tenant name | [default to null]
 **ArrangedSearchQuery** | [**ArrangedSearchQuery**](../Models/ArrangedSearchQuery.md)| A list of all search requests that should be part of a single response |

### Return type

[**SearchResult**](../Models/SearchResult.md)

### Authorization

[basic-auth](../index.md#basic-auth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: */*

<a name="getDocument"></a>
# **getDocument**
> Document getDocument(tenant, id)



### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **tenant** | **String**| tenant name | [default to null]
 **id** | **String**| document id | [default to null]

### Return type

[**Document**](../Models/Document.md)

### Authorization

[basic-auth](../index.md#basic-auth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: */*

<a name="getTenants"></a>
# **getTenants**
> getTenants()



### Parameters
This endpoint does not need any parameter.

### Return type

null (empty response body)

### Authorization

[basic-auth](../index.md#basic-auth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

<a name="search"></a>
# **search**
> SearchResult search(tenant, searchQuery, filters)

Search for documents

    Runs a search request for a certain tenant. The tenant should exist at the service and linked to a certain index in the backend. Different tenants may use the same index.

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **tenant** | **String**| tenant name | [default to null]
 **searchQuery** | [**SearchQuery**](../Models/.md)| the query that describes the wished result | [default to null]
 **filters** | [**Map**](../Models/String.md)| Any other parameters are used as filters. They are validated according to the actual data and configuration. Each filter can have multiple values, separated by comma. Commas inside the values have to be double-URL encoded. Depending on the configured backend type these values are used differently. | [optional] [default to null]

### Return type

[**SearchResult**](../Models/SearchResult.md)

### Authorization

[basic-auth](../index.md#basic-auth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: */*, text/plain

