# Indexer Service

<!-- TOC start -->
* [Overview](#overview)
* [Produced data structure](#produced-data-structure)
  + [searchData](#searchdata)
    - [minimal analyzer](#minimal-analyzer)
    - [standard analyzer](#standard-analyzer)
    - [shingles analyzer](#shingles-analyzer)
    - [ngram analyzer](#ngram-analyzer)
  + [resultData](#resultdata)
  + [sortData](#sortdata)
  + [scores](#scores)
  + [termFacetData](#termfacetdata)
  + [numberFacetData](#numberfacetdata)
  + [pathFacetData](#pathfacetdata)
  + [variants](#variants)
* [Modifying data](#modifying-data)
<!-- TOC end -->

## Overview

The Indexer is split into 2 main APIs: The full-indexation-API and the update-API.
It is implemented with Java ontop of Spring Boot. At the moment it expects an Elasticsearch Cluster as backend. 

The goal of that service is to receive simple data structure - in the simplest form it could be key-value documents, similar to CSV records - and transform them into a generic data structure, that automatically is analyzed and indexed by Elasticsearch for the according usages: searching, sorting, ranking and building facets. 

## Produced data structure
The separation by usage is achieved by putting the different data fields into according object-fields for the particular purpose. For each usage type, there is such a special "super field".
For example search relevant data like the "title" is put into `{"searchData": {"title":"..."}}`. This way we can instruct Elasticsearch using a template to analyze and index everything with the path `searchData.*` as searchable content.
If a data field is used for different things, e.g. "searching" and "sorting", that data field is indexed twice. Once as a searchable field at `searchData.<field>` and once as a sortable field at `sortData.<field>`.

Before going into detail about each single "super field", have a look at this simple example document with all those super fields.
The mapping for that data structure can be found at the [structured search template](../indexer-service/src/main/resources/elasticsearch/_template/structured_search.json).

```
{
  "id" : "100014881",
  "searchData" : {
    "title": "some product title"
  },
  "sortData" : {
    "title": "some product title"
    "price" : [
      99.9, 129.0
    ],
  },
  "scores" : {
    "stock" : 7
  },
  "resultData" : {
    "content": "some content you need in the search response",
    "multi_value_content" : ["...", "..."]
  },
  "termFacetData" : [
    {
      "name" : "brand",
      "value" : "Brand XY"
    },
    {
      "name" : "material",
      "value" : "wool",
      "id": "1234"
    },
  ],
  "numberFacetData" : [
    {
      "name" : "weight",
      "value" : 520
    }
  ],
  "pathFacetData" : [
    {
      "name" : "category",
      "id" : "1",
      "value" : "Category A"
    },
    {
      "name" : "category",
      "id" : "1_1",
      "value" : "Category A/Category B"
    },
    {
      "name" : "category",
      "id" : "1_2",
      "value" : "Category A/Category B/Category C"
    }
  ],
  "variants": [
    {
      "searchData": {
        "color": "red"
      },
      "termFacetData" : [
        {
          "name" : "color",
          "value" : "red",
          "id": "ff0000"
        },
      ],
      "numberFacetData" : [
        {
          "name" : "price",
          "value" : 129.0
        }
      ],
      "sortData": {
        "price": [129.0]
      }
    },
    {
      "searchData": {
        "color": "black"
      },
      "termFacetData" : [
        {
          "name" : "color",
          "value" : "black",
          "id": "000"
        },
      ],
      "numberFacetData" : [
        {
          "name" : "price",
          "value" : 99.9
        }
      ],
      "sortData": {
        "price": [99.9]
      }
    }
  ]
}
```

Now let's get into the detail for each super field:

### searchData

Contains searchable content prepared for full-text search. Each data field is indexed with several analyzers using the [multi-field feature of Elasticsearch](https://www.elastic.co/guide/en/elasticsearch/reference/current/multi-fields.html#_multi_fields_with_multiple_analyzers).
These are the prepared analyzers included in OCS:

#### minimal analyzer
- uses a html filter (that removes html tags and decodes html entities) 
- normalizes all content into lowercase
- replaces all non-letter characters by whitespace
- tokenizes the text by whitespace

It is used for the basic search field.
The aim is to persist the searchable content as much as possible only using normalization that doesn't loose relevant information.

#### standard analyzer
This is actually the [standard analyzer from Elasticsearch](https://www.elastic.co/guide/en/elasticsearch/reference/current/analysis-standard-analyzer.html). 
It is used at the 'standard' subfield, so for example at 'searchData.title.standard'.

It handles stemming + removes stopwords. If the same analyzer is used on the user query, it manages to match according keywords.

#### shingles analyzer
It has the aim to find compound query terms, that are actually decompound inside the data (e.g. "mousepad").
This is why it glues together adjacent terms to so called shingles. 
For example "a brown lazzy dogs" becomes "abrown brownlazzy lazzydog".

See the details of the analyzer inside of the template (not documented here, because they may change for some reason).

It is used at the 'shingle' sub field for each data field.

#### ngram analyzer
This analyzer splits the search content into 3-character fragments - so called 'ngrams'. 
The aim of this analyzer is to find partial matches of the user query. However it may also lead to many irrelevant matches, so it should be used with care.

See the details of the analyzer inside of the template (not documented here, because they may change for some reason).

It is used at the 'ngram' sub field for each data field.


### resultData

This data is not indexed at all. It should be used to attach any kind data to a document that is necessary in the search result later. 
If data fields are already attached as searchData, sortData or scores, it is not necessary to add them to resultData anymore. 
The only exception would be, if that data is modified at the other super fields.


### sortData

Data at this super field is indexed as is (type keyword), to guarantee correct a correct order of the result if sorted by the according data field.
For products with multiple variants that have different numeric values for a data field (e.g. different prices), the minimum and maximum of these values is stored at the main product. This way a correct ascending and descending order is achieved.


### scores

This super fields only supports numeric data or text values that can be parsed as date. [Check all supported formats at the according Elasticsearch documentation](https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-date-format.html).
Data in this field is indexed to be used at [function score queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html).


### termFacetData

To support an arbitrary amount of different facets without the need to know all those field names, the key-value data is changed into a object structure with 'name' and 'value' property.
Optionally it's also possible to index an ID that identifies that specific facet value.
At the search api it is then possible to use filters with that ID, e.g. `color.id=ff0000`

To ensure a name, value and optionally an ID always are considered as a "consistent unit", all facet super fields are indexed as nested documents.
This may be bad for performance, but works good to generate the best matching facets for a search result.


### numberFacetData

Similar to termFacetData, just with numeric values. This allows to do range filter on such attributes.
However numeric values do not support IDs, because it would not be possible to do range filters on those.
For numeric values that should be selected as single values (e.g. "size=128") you should index them as termFacetData.


### pathFacetData

Similar to termFacetData with the difference that each value is splitted by slash and lower path values are prefixed with the upper values.
Example: the path 'A/B/C' is split into 3 values: 'A', 'A/B', 'A/B/C'


### variants

Variants are nested documents that support the full structure of normal documents, including termFacetData and numberFacetData. The only difference is that they don't have IDs (nested documents in Elasticsearch can't have IDs) and pathFacetData are also not supported (YAGNI).


## Modifying data

In the best case, the incoming data should alread be cleansed as much as possible. However if there is no control of the source, it's also possible to modify the data as part of the ingester process.
Therefor you can implement a `DataPreProcessor` that fixes the data, befor it is converted into the internal format.

Sometimes it also makes sense, to change some data fields only for a specific usage type. For example to normalize brands only for their facet appearance or strip irrelevant words off product titles in the searchData subfield.
In such cases you can use a `DataPostProcessor` that has access to all the super fields listed above.

These processors can be included into the Ingester using the [plugin mechanics](plugin_guide.md)
