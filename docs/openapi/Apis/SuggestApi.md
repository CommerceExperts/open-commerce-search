# SuggestApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**suggest**](SuggestApi.md#suggest) | **GET** /suggest-api/v1/{indexname}/suggest | Autocomplete the user input


<a name="suggest"></a>
# **suggest**
> List suggest(indexname, userQuery, limit, filter)

Autocomplete the user input

    Runs a suggestion request on the data of a certain index.

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **indexname** | **String**| index name that should be searched for autocompletions | [default to null]
 **userQuery** | **String**| the simple raw query typed by the user | [default to null]
 **limit** | **Integer**| A optional limit for the suggestions | [optional] [default to null]
 **filter** | **String**| Optional comma-separated list of filter values. | [optional] [default to null]

### Return type

[**List**](../Models/Suggestion.md)

### Authorization

[basic-auth](../index.md#basic-auth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: */*, text/plain

