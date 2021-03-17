---
title: Open Commerce Search API v0.2
language_tabs:
  - java: Java
  - javascript: JavaScript
language_clients:
  - java: ""
  - javascript: ""
toc_footers: []
includes: []
search: false
highlight_theme: darkula
headingLevel: 2

---

<!-- Generator: Widdershins v4.0.1 -->

<h1 id="open-commerce-search-api">Open Commerce Search API v0.2</h1>

> Scroll down for code samples, example requests and responses. Select a language for code samples from the tabs above or the mobile navigation menu.

A common product search API that separates its usage from required search expertise

Email: <a href="mailto:info@commerce-experts.com">Support</a> 
License: <a href="http://www.apache.org/licenses/LICENSE-2.0.html">Apache 2.0</a>

# Authentication

- HTTP Authentication, scheme: basic 

<h1 id="open-commerce-search-api-search">search</h1>

## Autocomplete the user input

<a id="opIdsuggest"></a>

> Code samples

```java
URL obj = new URL("/suggest-api/v1/suggest/{indexname}?userQuery=string");
HttpURLConnection con = (HttpURLConnection) obj.openConnection();
con.setRequestMethod("GET");
int responseCode = con.getResponseCode();
BufferedReader in = new BufferedReader(
    new InputStreamReader(con.getInputStream()));
String inputLine;
StringBuffer response = new StringBuffer();
while ((inputLine = in.readLine()) != null) {
    response.append(inputLine);
}
in.close();
System.out.println(response.toString());

```

```javascript

const headers = {
  'Accept':'*/*'
};

fetch('/suggest-api/v1/suggest/{indexname}?userQuery=string',
{
  method: 'GET',

  headers: headers
})
.then(function(res) {
    return res.json();
}).then(function(body) {
    console.log(body);
});

```

`GET /suggest-api/v1/suggest/{indexname}`

Runs a suggestion request on the data of a certain index.

<h3 id="autocomplete-the-user-input-parameters">Parameters</h3>

|Name|In|Type|Required|Description|
|---|---|---|---|---|
|index|path|string|true|index name that should be searched for autocompletions|
|userQuery|query|string|true|the simple raw query typed by the user|
|filters|query|object|false|Any other parameter is used as filter.|

> Example responses

> 200 Response

<h3 id="autocomplete-the-user-input-responses">Responses</h3>

|Status|Meaning|Description|Schema|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|successful found results|Inline|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|tenant is unknown or index does not exist|None|

<h3 id="autocomplete-the-user-input-responseschema">Response Schema</h3>

Status Code **200**

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|*anonymous*|[[Suggestion](#schemasuggestion)]|false|none|none|
|» phrase|string|false|none|The phrase that is suggested and/or used as suggestion label.|
|» type|string|false|none|Optional type of that suggestion. Should be different for the different kind of suggested data. Default: 'keyword'|
|» payload|object|false|none|arbitrary payload attached to that suggestion. Default: null|
|»» **additionalProperties**|object|false|none|arbitrary payload attached to that suggestion. Default: null|

<aside class="warning">
To perform this operation, you must be authenticated by means of one of the following methods:
basic-auth
</aside>

## getTenants

<a id="opIdgetTenants"></a>

> Code samples

```java
URL obj = new URL("/search-api/v1/tenants");
HttpURLConnection con = (HttpURLConnection) obj.openConnection();
con.setRequestMethod("GET");
int responseCode = con.getResponseCode();
BufferedReader in = new BufferedReader(
    new InputStreamReader(con.getInputStream()));
String inputLine;
StringBuffer response = new StringBuffer();
while ((inputLine = in.readLine()) != null) {
    response.append(inputLine);
}
in.close();
System.out.println(response.toString());

```

```javascript

fetch('/search-api/v1/tenants',
{
  method: 'GET'

})
.then(function(res) {
    return res.json();
}).then(function(body) {
    console.log(body);
});

```

`GET /search-api/v1/tenants`

<h3 id="gettenants-responses">Responses</h3>

|Status|Meaning|Description|Schema|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|a list of available tenants|None|

<aside class="warning">
To perform this operation, you must be authenticated by means of one of the following methods:
basic-auth
</aside>

## Search for documents

<a id="opIdsearch"></a>

> Code samples

```java
URL obj = new URL("/search-api/v1/search/{tenant}?searchQuery=q,string,sort,string,limit,1,offset,0,withFacets,true,userQuery,%5Bobject%20Object%5D");
HttpURLConnection con = (HttpURLConnection) obj.openConnection();
con.setRequestMethod("GET");
int responseCode = con.getResponseCode();
BufferedReader in = new BufferedReader(
    new InputStreamReader(con.getInputStream()));
String inputLine;
StringBuffer response = new StringBuffer();
while ((inputLine = in.readLine()) != null) {
    response.append(inputLine);
}
in.close();
System.out.println(response.toString());

```

```javascript

const headers = {
  'Accept':'*/*'
};

fetch('/search-api/v1/search/{tenant}?searchQuery=q,string,sort,string,limit,1,offset,0,withFacets,true,userQuery,%5Bobject%20Object%5D',
{
  method: 'GET',

  headers: headers
})
.then(function(res) {
    return res.json();
}).then(function(body) {
    console.log(body);
});

```

`GET /search-api/v1/search/{tenant}`

Runs a search request for a certain tenant. The tenant should exist at the service and linked to a certain index in the backend. Different tenants may use the same index.

<h3 id="search-for-documents-parameters">Parameters</h3>

|Name|In|Type|Required|Description|
|---|---|---|---|---|
|tenant|path|string|true|tenant name|
|searchQuery|query|[SearchQuery](#schemasearchquery)|true|the query that describes the wished result|
|filters|query|object|false|Any other parameters are used as filters. They are validated according to the actual data and configuration. Each filter can have multiple values, separated by comma. Commas inside the values have to be double-URL encoded. Depending on the configured backend type these values are used differently.|

> Example responses

> 200 Response

<h3 id="search-for-documents-responses">Responses</h3>

|Status|Meaning|Description|Schema|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|successful found results|[SearchResult](#schemasearchresult)|
|204|[No Content](https://tools.ietf.org/html/rfc7231#section-6.3.5)|Optional response code that represents 'no result'|[SearchResult](#schemasearchresult)|
|403|[Forbidden](https://tools.ietf.org/html/rfc7231#section-6.5.3)|tenant can't be accessed or does not exist|None|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|response code if tenant is unknown or index does not exist|None|

<h3 id="search-for-documents-responseschema">Response Schema</h3>

<aside class="warning">
To perform this operation, you must be authenticated by means of one of the following methods:
basic-auth
</aside>

<h1 id="open-commerce-search-api-indexer">indexer</h1>

## cancel

<a id="opIdcancel"></a>

> Code samples

```java
URL obj = new URL("/indexer-api/v1/full/cancel");
HttpURLConnection con = (HttpURLConnection) obj.openConnection();
con.setRequestMethod("POST");
int responseCode = con.getResponseCode();
BufferedReader in = new BufferedReader(
    new InputStreamReader(con.getInputStream()));
String inputLine;
StringBuffer response = new StringBuffer();
while ((inputLine = in.readLine()) != null) {
    response.append(inputLine);
}
in.close();
System.out.println(response.toString());

```

```javascript
const inputBody = '{
  "finalIndexName": "string",
  "temporaryIndexName": "string"
}';
const headers = {
  'Content-Type':'*/*'
};

fetch('/indexer-api/v1/full/cancel',
{
  method: 'POST',
  body: inputBody,
  headers: headers
})
.then(function(res) {
    return res.json();
}).then(function(body) {
    console.log(body);
});

```

`POST /indexer-api/v1/full/cancel`

Cancels the import and in case there was an index created, it will be deleted.

> Body parameter

<h3 id="cancel-parameters">Parameters</h3>

|Name|In|Type|Required|Description|
|---|---|---|---|---|
|body|body|[ImportSession](#schemaimportsession)|true|none|

<h3 id="cancel-responses">Responses</h3>

|Status|Meaning|Description|Schema|
|---|---|---|---|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|indexation was already confirmed or import session is invalid|None|

<aside class="warning">
To perform this operation, you must be authenticated by means of one of the following methods:
basic-auth
</aside>

## done

<a id="opIddone"></a>

> Code samples

```java
URL obj = new URL("/indexer-api/v1/full/done");
HttpURLConnection con = (HttpURLConnection) obj.openConnection();
con.setRequestMethod("POST");
int responseCode = con.getResponseCode();
BufferedReader in = new BufferedReader(
    new InputStreamReader(con.getInputStream()));
String inputLine;
StringBuffer response = new StringBuffer();
while ((inputLine = in.readLine()) != null) {
    response.append(inputLine);
}
in.close();
System.out.println(response.toString());

```

```javascript
const inputBody = '{
  "finalIndexName": "string",
  "temporaryIndexName": "string"
}';
const headers = {
  'Content-Type':'*/*'
};

fetch('/indexer-api/v1/full/done',
{
  method: 'POST',
  body: inputBody,
  headers: headers
})
.then(function(res) {
    return res.json();
}).then(function(body) {
    console.log(body);
});

```

`POST /indexer-api/v1/full/done`

Finishes the import, flushing the new index and (in case there is already an index with the initialized name) replacing the old one.

> Body parameter

<h3 id="done-parameters">Parameters</h3>

|Name|In|Type|Required|Description|
|---|---|---|---|---|
|body|body|[ImportSession](#schemaimportsession)|true|none|

<h3 id="done-responses">Responses</h3>

|Status|Meaning|Description|Schema|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|successfully done|None|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|indexation was already confirmed or import session is invalid|None|

<aside class="warning">
To perform this operation, you must be authenticated by means of one of the following methods:
basic-auth
</aside>

## startImport

<a id="opIdstartImport"></a>

> Code samples

```java
URL obj = new URL("/indexer-api/v1/full/start/{indexName}?locale=string");
HttpURLConnection con = (HttpURLConnection) obj.openConnection();
con.setRequestMethod("GET");
int responseCode = con.getResponseCode();
BufferedReader in = new BufferedReader(
    new InputStreamReader(con.getInputStream()));
String inputLine;
StringBuffer response = new StringBuffer();
while ((inputLine = in.readLine()) != null) {
    response.append(inputLine);
}
in.close();
System.out.println(response.toString());

```

```javascript

const headers = {
  'Accept':'*/*'
};

fetch('/indexer-api/v1/full/start/{indexName}?locale=string',
{
  method: 'GET',

  headers: headers
})
.then(function(res) {
    return res.json();
}).then(function(body) {
    console.log(body);
});

```

`GET /indexer-api/v1/full/start/{indexName}`

Starts a new full import. Returns a handle containing meta data, that has to be passed to all following calls.

<h3 id="startimport-parameters">Parameters</h3>

|Name|In|Type|Required|Description|
|---|---|---|---|---|
|indexName|path|string|true|index name, that should match the regular expression '[a-z0-9_-]+'|
|locale|query|string|true|used for language dependent settings|

> Example responses

> 200 Response

<h3 id="startimport-responses">Responses</h3>

|Status|Meaning|Description|Schema|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|import session started|[ImportSession](#schemaimportsession)|
|409|[Conflict](https://tools.ietf.org/html/rfc7231#section-6.5.8)|there is already an import running for that index|None|

<aside class="warning">
To perform this operation, you must be authenticated by means of one of the following methods:
basic-auth
</aside>

## add

<a id="opIdadd"></a>

> Code samples

```java
URL obj = new URL("/indexer-api/v1/full/add");
HttpURLConnection con = (HttpURLConnection) obj.openConnection();
con.setRequestMethod("POST");
int responseCode = con.getResponseCode();
BufferedReader in = new BufferedReader(
    new InputStreamReader(con.getInputStream()));
String inputLine;
StringBuffer response = new StringBuffer();
while ((inputLine = in.readLine()) != null) {
    response.append(inputLine);
}
in.close();
System.out.println(response.toString());

```

```javascript
const inputBody = '{
  "documents": [
    {
      "id": "string",
      "data": {
        "property1": {},
        "property2": {}
      },
      "attributes": [
        {
          "id": "a.maxSpeed",
          "label": "Max Speed",
          "value": "230 km/h",
          "code": 230
        }
      ],
      "categories": [
        [
          {
            "id": "string",
            "name": "string"
          }
        ]
      ]
    }
  ]
}';
const headers = {
  'Content-Type':'*/*',
  'Accept':'*/*'
};

fetch('/indexer-api/v1/full/add',
{
  method: 'POST',
  body: inputBody,
  headers: headers
})
.then(function(res) {
    return res.json();
}).then(function(body) {
    console.log(body);
});

```

`POST /indexer-api/v1/full/add`

Add one or more documents to a running import session.

> Body parameter

<h3 id="add-parameters">Parameters</h3>

|Name|In|Type|Required|Description|
|---|---|---|---|---|
|body|body|[BulkImportData](#schemabulkimportdata)|true|Data that contains the import session reference and one or more documents that should be added to that session.|

> Example responses

> 200 Response

<h3 id="add-responses">Responses</h3>

|Status|Meaning|Description|Schema|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|documents successfully added|string|
|400|[Bad Request](https://tools.ietf.org/html/rfc7231#section-6.5.1)|import session is invalid|None|

<aside class="warning">
To perform this operation, you must be authenticated by means of one of the following methods:
basic-auth
</aside>

<h1 id="open-commerce-search-api-update">update</h1>

## putDocument

<a id="opIdputDocument"></a>

> Code samples

```java
URL obj = new URL("/indexer-api/v1/update/{indexName}");
HttpURLConnection con = (HttpURLConnection) obj.openConnection();
con.setRequestMethod("PUT");
int responseCode = con.getResponseCode();
BufferedReader in = new BufferedReader(
    new InputStreamReader(con.getInputStream()));
String inputLine;
StringBuffer response = new StringBuffer();
while ((inputLine = in.readLine()) != null) {
    response.append(inputLine);
}
in.close();
System.out.println(response.toString());

```

```javascript

fetch('/indexer-api/v1/update/{indexName}',
{
  method: 'PUT'

})
.then(function(res) {
    return res.json();
}).then(function(body) {
    console.log(body);
});

```

`PUT /indexer-api/v1/update/{indexName}`

Puts a document to the index. If document does not exist, it will be added. An existing product will be overwritten unless the parameter 'replaceExisting" is set to "false". Provided document should be a complete object, partial updates should be  done using the updateDocument method.

<h3 id="putdocument-parameters">Parameters</h3>

|Name|In|Type|Required|Description|
|---|---|---|---|---|
|indexName|path|string|true|none|
|replaceExisting|query|boolean|false|set to false to avoid overriding a document with that ID. Defaults to 'true'|

<h3 id="putdocument-responses">Responses</h3>

|Status|Meaning|Description|Schema|
|---|---|---|---|
|201|[Created](https://tools.ietf.org/html/rfc7231#section-6.3.2)|Document created|None|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|index does not exist|None|
|409|[Conflict](https://tools.ietf.org/html/rfc7231#section-6.5.8)|Document already exists but replaceExisting is set to false|None|

<aside class="warning">
To perform this operation, you must be authenticated by means of one of the following methods:
basic-auth
</aside>

## deleteDocument

<a id="opIddeleteDocument"></a>

> Code samples

```java
URL obj = new URL("/indexer-api/v1/update/{indexName}");
HttpURLConnection con = (HttpURLConnection) obj.openConnection();
con.setRequestMethod("DELETE");
int responseCode = con.getResponseCode();
BufferedReader in = new BufferedReader(
    new InputStreamReader(con.getInputStream()));
String inputLine;
StringBuffer response = new StringBuffer();
while ((inputLine = in.readLine()) != null) {
    response.append(inputLine);
}
in.close();
System.out.println(response.toString());

```

```javascript

fetch('/indexer-api/v1/update/{indexName}',
{
  method: 'DELETE'

})
.then(function(res) {
    return res.json();
}).then(function(body) {
    console.log(body);
});

```

`DELETE /indexer-api/v1/update/{indexName}`

Delete existing document. If document does not exist, it returns code 304.

<h3 id="deletedocument-parameters">Parameters</h3>

|Name|In|Type|Required|Description|
|---|---|---|---|---|
|indexName|path|string|true|none|
|id|query|string|false|none|

<h3 id="deletedocument-responses">Responses</h3>

|Status|Meaning|Description|Schema|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|document deleted|None|
|304|[Not Modified](https://tools.ietf.org/html/rfc7232#section-4.1)|document not found|None|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|index does not exist|None|

<aside class="warning">
To perform this operation, you must be authenticated by means of one of the following methods:
basic-auth
</aside>

## patchDocument

<a id="opIdpatchDocument"></a>

> Code samples

```java
URL obj = new URL("/indexer-api/v1/update/{indexName}");
HttpURLConnection con = (HttpURLConnection) obj.openConnection();
con.setRequestMethod("PATCH");
int responseCode = con.getResponseCode();
BufferedReader in = new BufferedReader(
    new InputStreamReader(con.getInputStream()));
String inputLine;
StringBuffer response = new StringBuffer();
while ((inputLine = in.readLine()) != null) {
    response.append(inputLine);
}
in.close();
System.out.println(response.toString());

```

```javascript

fetch('/indexer-api/v1/update/{indexName}',
{
  method: 'PATCH'

})
.then(function(res) {
    return res.json();
}).then(function(body) {
    console.log(body);
});

```

`PATCH /indexer-api/v1/update/{indexName}`

Partial update of an existing document. If the document does not exist, no update will be performed and status code 404 is returned. In case the document is a master product with variants, the provided master product may only contain the changed values. However if some data at the product variants are updated, all data from all variant products are required, otherwise missing variants won't be there after the update! This is how single variants can be deleted.

<h3 id="patchdocument-parameters">Parameters</h3>

|Name|In|Type|Required|Description|
|---|---|---|---|---|
|indexName|path|string|true|none|

<h3 id="patchdocument-responses">Responses</h3>

|Status|Meaning|Description|Schema|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|document successfuly patched|None|
|404|[Not Found](https://tools.ietf.org/html/rfc7231#section-6.5.4)|index does not exist or document not found|None|

<aside class="warning">
To perform this operation, you must be authenticated by means of one of the following methods:
basic-auth
</aside>

# Schemas

<h2 id="tocS_Suggestion">Suggestion</h2>
<!-- backwards compatibility -->
<a id="schemasuggestion"></a>
<a id="schema_Suggestion"></a>
<a id="tocSsuggestion"></a>
<a id="tocssuggestion"></a>

```json
{
  "phrase": "string",
  "type": "keyword, brand, category, product",
  "payload": {
    "property1": {},
    "property2": {}
  }
}

```

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|phrase|string|false|none|The phrase that is suggested and/or used as suggestion label.|
|type|string|false|none|Optional type of that suggestion. Should be different for the different kind of suggested data. Default: 'keyword'|
|payload|object|false|none|arbitrary payload attached to that suggestion. Default: null|
|» **additionalProperties**|object|false|none|arbitrary payload attached to that suggestion. Default: null|

<h2 id="tocS_ImportSession">ImportSession</h2>
<!-- backwards compatibility -->
<a id="schemaimportsession"></a>
<a id="schema_ImportSession"></a>
<a id="tocSimportsession"></a>
<a id="tocsimportsession"></a>

```json
{
  "finalIndexName": "string",
  "temporaryIndexName": "string"
}

```

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|finalIndexName|string|true|none|none|
|temporaryIndexName|string|true|none|none|

<h2 id="tocS_Attribute">Attribute</h2>
<!-- backwards compatibility -->
<a id="schemaattribute"></a>
<a id="schema_Attribute"></a>
<a id="tocSattribute"></a>
<a id="tocsattribute"></a>

```json
{
  "id": "a.maxSpeed",
  "label": "Max Speed",
  "value": "230 km/h",
  "code": 230
}

```

Rich model that can be used to represent a document or product attribute. If 'id' and/or 'code' are provieded, these can be used for consistent filtering, even if the label and values are changing. The label and the values will be used used to produce nice facets or if used for search, they will be added to the searchable content.

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|id|string|false|none|Optional: Static ID of that attribute. The id SHOULD be URL friendly, since it could be used to build according filter parameters. If not set, the label could be used for parameter building.|
|label|string|true|none|Human readable name of the attribute, e.g. 'Color' or 'Max. Speed in km/h'|
|code|string|false|none|Optional: code that represents that attribute value, e.g. "FF0000" for color|
|value|string|true|none|Human readable representation of that attribute, e.g. 'Red' for the attribute 'Color'|

<h2 id="tocS_BulkImportData">BulkImportData</h2>
<!-- backwards compatibility -->
<a id="schemabulkimportdata"></a>
<a id="schema_BulkImportData"></a>
<a id="tocSbulkimportdata"></a>
<a id="tocsbulkimportdata"></a>

```json
{
  "session": {
    "finalIndexName": "string",
    "temporaryIndexName": "string"
  },
  "documents": [
    {
      "id": "string",
      "data": {
        "property1": {},
        "property2": {}
      },
      "attributes": [
        {
          "id": "a.maxSpeed",
          "label": "Max Speed",
          "value": "230 km/h",
          "code": 230
        }
      ],
      "categories": [
        [
          {
            "id": "string",
            "name": "string"
          }
        ]
      ]
    }
  ]
}

```

composite object that is used to add documents to the index.

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|session|[ImportSession](#schemaimportsession)|false|none|none|
|documents|[[Document](#schemadocument)]|true|none|[A data record that contains any data relevant for search. The single field types and conversions are part of the according service configuration.]|

<h2 id="tocS_Category">Category</h2>
<!-- backwards compatibility -->
<a id="schemacategory"></a>
<a id="schema_Category"></a>
<a id="tocScategory"></a>
<a id="tocscategory"></a>

```json
{
  "id": "string",
  "name": "string"
}

```

categories are treated in a parent-child relationship, so a product can be placed into a path within a category tree. Multiple category paths can be defined per document.

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|id|string|false|none|Optional ID for a consistent filtering|
|name|string|true|none|none|

<h2 id="tocS_Document">Document</h2>
<!-- backwards compatibility -->
<a id="schemadocument"></a>
<a id="schema_Document"></a>
<a id="tocSdocument"></a>
<a id="tocsdocument"></a>

```json
{
  "id": "string",
  "data": {
    "property1": {},
    "property2": {}
  },
  "attributes": [
    {
      "id": "a.maxSpeed",
      "label": "Max Speed",
      "value": "230 km/h",
      "code": 230
    }
  ],
  "categories": [
    [
      {
        "id": "string",
        "name": "string"
      }
    ]
  ]
}

```

A data record that contains any data relevant for search. The single field types and conversions are part of the according service configuration.

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|id|string|true|none|none|
|data|object|true|none|The data property should be used for standard fields, such as title, description, price. Only values of the following types are accepted (others will be dropped silently): Standard primitive types (Boolean, String, Integer, Double) and arrays of these types. Attributes (key-value objects with ID) should be passed to the attributes property.|
|» **additionalProperties**|object|false|none|The data property should be used for standard fields, such as title, description, price. Only values of the following types are accepted (others will be dropped silently): Standard primitive types (Boolean, String, Integer, Double) and arrays of these types. Attributes (key-value objects with ID) should be passed to the attributes property.|
|attributes|[[Attribute](#schemaattribute)]|false|none|multiple attributes can be delivered separately from standard data fields|
|categories|[array]|false|none|none|

<h2 id="tocS_Product">Product</h2>
<!-- backwards compatibility -->
<a id="schemaproduct"></a>
<a id="schema_Product"></a>
<a id="tocSproduct"></a>
<a id="tocsproduct"></a>

```json
{
  "id": "string",
  "data": {
    "property1": {},
    "property2": {}
  },
  "attributes": [
    {
      "id": "a.maxSpeed",
      "label": "Max Speed",
      "value": "230 km/h",
      "code": 230
    }
  ],
  "categories": [
    [
      {
        "id": "string",
        "name": "string"
      }
    ]
  ],
  "variants": [
    {
      "id": "string",
      "data": {
        "property1": {},
        "property2": {}
      },
      "attributes": [
        {
          "id": "a.maxSpeed",
          "label": "Max Speed",
          "value": "230 km/h",
          "code": 230
        }
      ],
      "categories": [
        [
          {
            "id": "string",
            "name": "string"
          }
        ]
      ]
    }
  ]
}

```

Main product containing the data that is common for all variants. A product may represent a master-variant relation ship. A variant should be associated to a single Product and cannot have variants again - those will be ignored. It should only contain data special to that variant. Data that is common to all variants should be set at master level.

### Properties

allOf

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|*anonymous*|[Document](#schemadocument)|false|none|A data record that contains any data relevant for search. The single field types and conversions are part of the according service configuration.|

and

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|*anonymous*|object|false|none|none|
|» variants|[[Document](#schemadocument)]|false|none|for products without variants, it can be null or rather us a document directly.|

<h2 id="tocS_Facet">Facet</h2>
<!-- backwards compatibility -->
<a id="schemafacet"></a>
<a id="schema_Facet"></a>
<a id="tocSfacet"></a>
<a id="tocsfacet"></a>

```json
{
  "fieldName": "string",
  "absoluteFacetCoverage": 0,
  "isFiltered": true,
  "entries": [
    {
      "type": "string",
      "key": "string",
      "id": "string",
      "docCount": 0,
      "link": "string",
      "selected": true
    }
  ],
  "type": "term",
  "meta": {
    "property1": {},
    "property2": {}
  },
  "filtered": true
}

```

If facets are part of this slice, they are placed here. By default only one slice SHOULD contain facets.

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|fieldName|string|false|none|This is the name coming from the data. Separate label information should be available in the meta data.|
|absoluteFacetCoverage|integer(int64)|false|none|This is the amount of matched documents that are covered by that facet.|
|isFiltered|boolean|false|none|Is set to true if there an active filter from that facet.|
|entries|[[FacetEntry](#schemafacetentry)]|false|none|The entries of that facet.|
|type|string|false|none|The type of the facet, so the kind of FacetEntries it contains. See the according FacetEntry variants for more details.|
|meta|object|false|none|Optional meta data for that facet, e.g. display hints like a label or a facet-type.|
|» **additionalProperties**|object|false|none|Optional meta data for that facet, e.g. display hints like a label or a facet-type.|
|filtered|boolean|false|none|none|

#### Enumerated Values

|Property|Value|
|---|---|
|type|term|
|type|hierarchical|
|type|interval|
|type|range|

<h2 id="tocS_FacetEntry">FacetEntry</h2>
<!-- backwards compatibility -->
<a id="schemafacetentry"></a>
<a id="schema_FacetEntry"></a>
<a id="tocSfacetentry"></a>
<a id="tocsfacetentry"></a>

```json
{
  "type": "string",
  "key": "string",
  "id": "string",
  "docCount": 0,
  "link": "string",
  "selected": true
}

```

The entries of that facet.

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|type|string|false|none|none|
|key|string|false|none|none|
|id|string|false|none|none|
|docCount|integer(int64)|false|none|Estimated amount of documents that will be returned, if this facet entry is picked as filter.|
|link|string(URI)|false|none|none|
|selected|boolean|false|none|Should be set to true in the response, if that filter is actually selected.|

<h2 id="tocS_HierarchialFacetEntry">HierarchialFacetEntry</h2>
<!-- backwards compatibility -->
<a id="schemahierarchialfacetentry"></a>
<a id="schema_HierarchialFacetEntry"></a>
<a id="tocShierarchialfacetentry"></a>
<a id="tocshierarchialfacetentry"></a>

```json
{
  "type": "string",
  "key": "string",
  "id": "string",
  "docCount": 0,
  "link": "string",
  "selected": true,
  "children": [
    {
      "type": "string",
      "key": "string",
      "id": "string",
      "docCount": 0,
      "link": "string",
      "selected": true
    }
  ],
  "path": "string"
}

```

### Properties

allOf - discriminator: FacetEntry.type

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|*anonymous*|[FacetEntry](#schemafacetentry)|false|none|The entries of that facet.|

and

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|*anonymous*|object|false|none|none|
|» type|string|false|none|none|
|» key|string|false|none|none|
|» id|string|false|none|none|
|» docCount|integer(int64)|false|none|Estimated amount of documents that will be returned, if this facet entry is picked as filter.|
|» link|string(URI)|false|none|none|
|» selected|boolean|false|none|Should be set to true in the response, if that filter is actually selected.|
|» children|[[FacetEntry](#schemafacetentry)]|false|none|Child facet entries to that particular facet. The child facets again could be HierarchialFacetEntries.|
|» path|string|false|none|none|

<h2 id="tocS_IntervalFacetEntry">IntervalFacetEntry</h2>
<!-- backwards compatibility -->
<a id="schemaintervalfacetentry"></a>
<a id="schema_IntervalFacetEntry"></a>
<a id="tocSintervalfacetentry"></a>
<a id="tocsintervalfacetentry"></a>

```json
{
  "type": "string",
  "key": "string",
  "id": "string",
  "docCount": 0,
  "link": "string",
  "selected": true,
  "lowerBound": 0,
  "upperBound": 0
}

```

Facet entry that describes a numerical interval. If only the lower value or only the upper value is set, this means it's an open ended interval, e.g. '< 100' for upper bound only.

### Properties

allOf - discriminator: FacetEntry.type

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|*anonymous*|[FacetEntry](#schemafacetentry)|false|none|The entries of that facet.|

and

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|*anonymous*|object|false|none|none|
|» type|string|false|none|none|
|» key|string|false|none|none|
|» id|string|false|none|none|
|» docCount|integer(int64)|false|none|Estimated amount of documents that will be returned, if this facet entry is picked as filter.|
|» link|string(URI)|false|none|none|
|» selected|boolean|false|none|Should be set to true in the response, if that filter is actually selected.|
|» lowerBound|number|false|none|none|
|» upperBound|number|false|none|none|

<h2 id="tocS_RangeFacetEntry">RangeFacetEntry</h2>
<!-- backwards compatibility -->
<a id="schemarangefacetentry"></a>
<a id="schema_RangeFacetEntry"></a>
<a id="tocSrangefacetentry"></a>
<a id="tocsrangefacetentry"></a>

```json
{
  "type": "string",
  "key": "string",
  "id": "string",
  "docCount": 0,
  "link": "string",
  "selected": true,
  "lowerBound": 0,
  "upperBound": 0,
  "selectedMin": 0,
  "selectedMax": 0
}

```

Facet entry that describes the complete range of the facet. If a filter is picked, the selectedMin and selectedMax value are set, otherwise null.

### Properties

allOf - discriminator: FacetEntry.type

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|*anonymous*|[FacetEntry](#schemafacetentry)|false|none|The entries of that facet.|

and

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|*anonymous*|object|false|none|none|
|» type|string|false|none|none|
|» key|string|false|none|none|
|» id|string|false|none|none|
|» docCount|integer(int64)|false|none|Estimated amount of documents that will be returned, if this facet entry is picked as filter.|
|» link|string(URI)|false|none|none|
|» selected|boolean|false|none|Should be set to true in the response, if that filter is actually selected.|
|» lowerBound|number|false|none|none|
|» upperBound|number|false|none|none|
|» selectedMin|number|false|none|none|
|» selectedMax|number|false|none|none|

<h2 id="tocS_ResultHit">ResultHit</h2>
<!-- backwards compatibility -->
<a id="schemaresulthit"></a>
<a id="schema_ResultHit"></a>
<a id="tocSresulthit"></a>
<a id="tocsresulthit"></a>

```json
{
  "index": "string",
  "document": {
    "id": "string",
    "data": {
      "property1": {},
      "property2": {}
    },
    "attributes": [
      {
        "id": "a.maxSpeed",
        "label": "Max Speed",
        "value": "230 km/h",
        "code": 230
      }
    ],
    "categories": [
      [
        {
          "id": "string",
          "name": "string"
        }
      ]
    ]
  },
  "matchedQueries": [
    "string"
  ]
}

```

the list of actual hits for that result view.

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|index|string|false|none|none|
|document|[Document](#schemadocument)|false|none|A data record that contains any data relevant for search. The single field types and conversions are part of the according service configuration.|
|matchedQueries|[string]|false|none|none|

<h2 id="tocS_SearchResult">SearchResult</h2>
<!-- backwards compatibility -->
<a id="schemasearchresult"></a>
<a id="schema_SearchResult"></a>
<a id="tocSsearchresult"></a>
<a id="tocssearchresult"></a>

```json
{
  "tookInMillis": 0,
  "inputURI": "string",
  "slices": [
    {
      "label": "string",
      "matchCount": 0,
      "nextOffset": 0,
      "nextLink": "string",
      "resultLink": "string",
      "hits": [
        {
          "index": "string",
          "document": {
            "id": "string",
            "data": {
              "property1": {},
              "property2": {}
            },
            "attributes": [
              {
                "id": "a.maxSpeed",
                "label": "Max Speed",
                "value": "230 km/h",
                "code": 230
              }
            ],
            "categories": [
              [
                {
                  "id": "string",
                  "name": "string"
                }
              ]
            ]
          },
          "matchedQueries": [
            "string"
          ]
        }
      ],
      "facets": [
        {
          "fieldName": "string",
          "absoluteFacetCoverage": 0,
          "isFiltered": true,
          "entries": [
            {
              "type": "string",
              "key": "string",
              "id": "string",
              "docCount": 0,
              "link": "string",
              "selected": true
            }
          ],
          "type": "term",
          "meta": {
            "property1": {},
            "property2": {}
          },
          "filtered": true
        }
      ]
    }
  ],
  "sortOptions": [
    {
      "field": "string",
      "sortOrder": "asc",
      "isActive": true,
      "link": "string",
      "active": true
    }
  ],
  "meta": {
    "property1": {},
    "property2": {}
  }
}

```

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|tookInMillis|integer(int64)|false|none|amount of time the internal search needed to compute that result|
|inputURI|string(URI)|false|none|The URI that was used to get that result view. May be used to generate breadcrumbs.|
|slices|[[SearchResultSlice](#schemasearchresultslice)]|false|none|The result may consist of several slices, for example if a search request couldn't be answered matching all words (e.g. "striped nike shirt") then one slice could be the result for one part of the query (e.g. "striped shirt") and the other could be for another part of the query (e.g. "nike shirt"). This can also be used to deliver some special advertised products or to split the result in different ranked slices (e.g. the first 3 results are ranked by popularity, the next 3 are sorted by price and the rest is ranked by 'default' relevance). Each slice contains the {@link SearchQuery} that represent that exact slice. At least 1 slice should be expected. If there is no slice, no results were found.|
|sortOptions|[[Sorting](#schemasorting)]|false|none|none|
|meta|object|false|none|none|
|» **additionalProperties**|object|false|none|none|

<h2 id="tocS_SearchResultSlice">SearchResultSlice</h2>
<!-- backwards compatibility -->
<a id="schemasearchresultslice"></a>
<a id="schema_SearchResultSlice"></a>
<a id="tocSsearchresultslice"></a>
<a id="tocssearchresultslice"></a>

```json
{
  "label": "string",
  "matchCount": 0,
  "nextOffset": 0,
  "nextLink": "string",
  "resultLink": "string",
  "hits": [
    {
      "index": "string",
      "document": {
        "id": "string",
        "data": {
          "property1": {},
          "property2": {}
        },
        "attributes": [
          {
            "id": "a.maxSpeed",
            "label": "Max Speed",
            "value": "230 km/h",
            "code": 230
          }
        ],
        "categories": [
          [
            {
              "id": "string",
              "name": "string"
            }
          ]
        ]
      },
      "matchedQueries": [
        "string"
      ]
    }
  ],
  "facets": [
    {
      "fieldName": "string",
      "absoluteFacetCoverage": 0,
      "isFiltered": true,
      "entries": [
        {
          "type": "string",
          "key": "string",
          "id": "string",
          "docCount": 0,
          "link": "string",
          "selected": true
        }
      ],
      "type": "term",
      "meta": {
        "property1": {},
        "property2": {}
      },
      "filtered": true
    }
  ]
}

```

The result may consist of several slices, for example if a search request couldn't be answered matching all words (e.g. "striped nike shirt") then one slice could be the result for one part of the query (e.g. "striped shirt") and the other could be for another part of the query (e.g. "nike shirt"). This can also be used to deliver some special advertised products or to split the result in different ranked slices (e.g. the first 3 results are ranked by popularity, the next 3 are sorted by price and the rest is ranked by 'default' relevance). Each slice contains the {@link SearchQuery} that represent that exact slice. At least 1 slice should be expected. If there is no slice, no results were found.

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|label|string|false|none|An identifier for that result slice. Can be used to differentiate different slices. Values depend on the implementation.|
|matchCount|integer(int64)|false|none|the absolute number of matches in this result.|
|nextOffset|integer(int64)|false|none|the offset value to use to get the next result batch|
|nextLink|string(URI)|false|none|URL conform query parameters, that has to be used to get the next bunch of results. Is null if there are no more results.|
|resultLink|string(URI)|false|none|The query that represents exact that passed slice. If send to the engine again, that slice should be returned as main result.|
|hits|[[ResultHit](#schemaresulthit)]|false|none|the list of actual hits for that result view.|
|facets|[[Facet](#schemafacet)]|false|none|If facets are part of this slice, they are placed here. By default only one slice SHOULD contain facets.|

<h2 id="tocS_Sorting">Sorting</h2>
<!-- backwards compatibility -->
<a id="schemasorting"></a>
<a id="schema_Sorting"></a>
<a id="tocSsorting"></a>
<a id="tocssorting"></a>

```json
{
  "field": "string",
  "sortOrder": "asc",
  "isActive": true,
  "link": "string",
  "active": true
}

```

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|field|string|false|none|none|
|sortOrder|string|false|none|none|
|isActive|boolean|false|none|none|
|link|string(URI)|false|none|none|
|active|boolean|false|none|none|

#### Enumerated Values

|Property|Value|
|---|---|
|sortOrder|asc|
|sortOrder|desc|

<h2 id="tocS_SearchQuery">SearchQuery</h2>
<!-- backwards compatibility -->
<a id="schemasearchquery"></a>
<a id="schema_SearchQuery"></a>
<a id="tocSsearchquery"></a>
<a id="tocssearchquery"></a>

```json
{
  "q": "string",
  "sort": "string",
  "limit": 1,
  "offset": 0,
  "withFacets": true,
  "userQuery": {
    "q": "string",
    "sort": "string",
    "limit": 1,
    "offset": 0,
    "withFacets": true,
    "userQuery": {}
  }
}

```

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|q|string|false|none|none|
|sort|string|false|none|none|
|limit|integer(int32)|false|none|none|
|offset|integer(int32)|false|none|none|
|withFacets|boolean|false|none|none|
|userQuery|[SearchQuery](#schemasearchquery)|false|none|none|

