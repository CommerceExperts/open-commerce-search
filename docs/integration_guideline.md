# Integration Guideline

This guideline has the goal to give an overview about how the OCSS-Services work from an integration perspective. With this guide no deeper understanding of OCSS is required.

The Open Commerce Search Stack is build with a focus of integration simplicity. A request to the search service is as simple as entering the endpoint URL in your browser. All parameters are optional.

Also indexation is meant to be as easy as throwing a few JSON documents against the Indexer endpoint. With our script [ocs-index-data.sh](https://github.com/CommerceExperts/open-commerce-search/tree/master/operations/ocs-index-data.sh) we have a basic reference implementation in bash.

**Disclaimer**: _This part of the documentation is Work-In-Progress and thus is yet incomplete and may contain spelling errors etc._


## Indexation

Before you can search anything, you need to add data to the search. This is done by addressing the separate "indexer-service" that runs at a separate endpoint.
The easiest way is to start with a full indexation and add at least a few documents or products. But before a brief understanding of a document is required.

### Document & Product Anatomy

With 'document' we use the same terminology as Elasticsearch. It basically is the single data record that is indexed and later retrievable as search hit.
OCSS limits the document to a quite flat "key-value" map with a few exceptions.

This is already a full document
```
{
    "id": "12300002",
    "data": {
        "title": "My first document",
        "rating": 4.5,
        "description": "As much content as necessary..."
    }
}
```

#### Attributes

Additional to the simple data map, a list of attributes can be added. They are useful if the inserted data values also need to be addressed by a standardized "code"/"id".

```
{
    "id": "123142112",
    "data": { "title": "Blue Shirt" },
    "attributes": [
        {"name": "color", "value": "blue", "code": "BLUE1"}
    ]
}
```

With that example you can later filter on that attribute by it's code/id:

```
    ${base_url}?color.id=BLUE1
```

(Side Note: The name 'code' is used for historical reasons, but now is mapped to 'id').


#### Product Variants

For an e-commerce environment sometimes products come with different variants, but all should be considered as single entity and also as a single hit in the search result. 
To achieve that, the `Document` model is extended to a `Product` which basically is a Document with sub-documents added at the `variants` property, where each is again a simple document.

Note:
Although you can import categories on variant level, its not possible to use the for faceting. Categories are only used for facetting on product level.

Example:

```
{
    "id": "12341",
    "data": {"title": "Shirt with Cat Print"},
    "variants": [
        {
            "id": "12341001",
            "data": {"color": "red"}
        },
        {
            "id": "12341002",
            "data": {"color": "black"}
        }
    ]
}
```

### Full Indexation API

A full indexation process is built in a session-like manner. (In terms of universality the examples are documented as curl requests.)

1. Start a full indexation session by specifying the "index name" and the "locale" for that index.

```
INDEX_NAME="Your_Index_Name"
curl -X GET "$ENDPOINT/indexer-api/v1/full/start/$INDEX_NAME?locale=en"
```

As a response a "session" object is returned. That session object has to be part of the following requests.

Example:
```
{ "finalIndexName": "your_index_name_normalized", "temporaryIndexName": "ocs-your_index_name-locale" }
```


2. Adding documents

Documents are expected to be added bulk wise. The size of the bulks can vary depending of the size of the documents and connection limitations.

```
curl -H 'Content-Type: application/json' \
     -X POST "$ENDPOINT/indexer-api/v1/full/add" \
     -d '{
        "session": { "finalIndexName": "your_index_name_normalized", "temporaryIndexName": "ocs-your_index_name-locale" },
        "documents": [
            {"id": "1230001", "data": {"title": "Shirt"}},
            {"id": "1433002", "data": {"title": "Shoes"}}
        ]
     }'
```

As a response a simple integer value is returned. It tells the number of successful indexed documents. If a single document has structural errors and can't be validated, it is simply dropped and the returned value is lower than the amount of inserted documents.


3. Finalizing the full indexation

If all went fine and as expected, a "done" request needs to be sent with the according session object in order to complete the full indexation.

```
curl -H 'Content-Type: application/json' \
     -X POST "$ENDPOINT/indexer-api/v1/full/done" \
     -d '{ "finalIndexName": "your_index_name_normalized", "temporaryIndexName": "ocs-your_index_name-locale" }'
```

Afterwards that index is live and usable for requests trought the search service. For a new full indexation into the same index use the same index name. 
The indexed data won't be available for the search service unless that done request is sent.

If something went wrong it's also possible to send a "cancel" request.

```
curl -H 'Content-Type: application/json' \
     -X POST "$ENDPOINT/indexer-api/v1/full/cancel" \
     -d '{ "finalIndexName": "your_index_name_normalized", "temporaryIndexName": "ocs-your_index_name-locale" }'
```

In that case all the indexed data so far will be dropped.

Trivia:
- Indexes that are not completed for "a longer time", will be deleted. 
  Abandoned indexes are only checked if a new full-indexation is started. The previous indexes are then also only deleted if older than 1 hour.

- Starting two or more full indexation processes is possible (if started within 1 hour). The final "done" request decides which of them will be used afterwards.

- It is possible to have several index services running parallel in a load-balanced scenario (that are connected to the same Elasticsearch instance) and send index requests to both of them, 
  as long as the same session object is used (which basically carries the information into which Elasticsearch-index that data is sent).


### Update API

TBD

## Searching

TBD

### Basic Request & Response

TBD

#### Request

TODO:
- userQuery
- paging with offset and limit
- filter paramters
- sort parameters

#### Result

TODO:
- explain slices
- hits
- facets and filters
- sort options
- meta data


### Arranged Search

In parallel to the Search GET-API there is also the POST API that comes with one more possibility: injecting explicitly defined documents into the "natural" search result.
Therefore one or more 'product sets' are defined with the request. The products defined by those product sets are then part of the response in the specified order.

TODO: difference between static and dynamic product sets


#### Variant Boosting

Since variant documents don’t have an addressable ID in Elasticsearch, there is the option to add a “variantBoostTerm” string. This is a string of whitespace separated terms that are used to boost variants that match to any of the given terms (the more the better). Depending on the configured variants-picking-strategy those variants are then also part of the response.

For static ID lists this means, that you can pass the list of variantIds white-space separated string. The according variants will then be prefered:

```
    "arrangedProductSets": [
        {
            "name": "heros",
            "type": "static",
            "variantBoostTerms": "100359698_003",
            "ids": ["100331685","100359698"]
        }
    ]
```

If the “master IDs” are unknown, dynamic sets can also be used to search for the variant articleNumbers/EANs/IDs directly (which works only if they are indexed for search). At dynamic products sets the query is used for variant boosting as well. However with this solution the order of the IDs is not guaranteed.

```
    "arrangedProductSets": [
        {
            "name": "heros",
            "type": "dynamic",
            "query": "100331685 100359698_003"
        }
    ]
```

Actually the `variantBoostTerms` property at dynamic product sets is rather meant to boost according searchable variant content, i.e. to show any shirts but prefer red ones if available:

```
    "arrangedProductSets": [
        {
            "name": "hero shirts",
            "type": "dynamic",
            "query": "shirts",
            "variantBoostTerms": "red"
        }
    ]
```


## Suggest

TBD
