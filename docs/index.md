
## Contents

- At this [Overview](#overview) you may grasp an overall understanding about the whole stack.
- At the [quick start demo](quick_start_demo.md) you learn how to get the OCSS up and running locally with some sample data.
- The [Open-API Docs](generated_api_doc.md) is a nice view on the [Open API Spec](https://github.com/CommerceExperts/open-commerce-search/tree/master/open-commerce-search-api/src/main/resources/openapi.yaml).
- The [operations directory](https://github.com/CommerceExperts/open-commerce-search/tree/master/operations) contains a basic [docker-compose setup](https://github.com/CommerceExperts/open-commerce-search/tree/master/operations/docker-compose), a [Kubernetes tutorial](https://github.com/CommerceExperts/open-commerce-search/tree/master/operations/k8s) and other tools.
- Learn how to configure the OCS Services at the [Configuration section](configuration.md)
- The details of the services are described here:
  - [Indexer Service](indexer_service.md)
  - [Search Service](search_service.md)
  - [Suggest Service](suggest_service.md)
- Learn how to customize OCSS with the [Plugin Guide](plugin_guide.md)
- Finally you might be interested in the projects [complete JavaDoc](apidocs/).


## Overview

The Open Commerce Search Stack (OCSS) is a small abstraction layer on top of existing open source search solutions (Elasticsearch, Lucene and Querqy) that removes the hastle of dealing with the details of information retrieval. It was designed to handle most problems of search in e-commerce using known best practices. 
The API first approach aims for simplicity and broad adaption.
[More information about the motivation and background of OCSS can be found in our blog](https://blog.searchhub.io/introducing-open-commerce-search-stack-ocss).

The OCS-Stack is devided into 3 services: Indexer, Search- and Suggest-Service.

```
 Your Side            .  OCS Stack              .  Elasticsearch
                      .                         .
                      .                         .  either selfhosted
┌───────────────────┐ .                         .  or some SaaS/Cloud offering
│ your data source, │ .                         .
│ e.g. db, csv, etc.│ .                         .
└───────▲───────────┘ .                         .
        │             .                         .
┌───────┴───────────┐ .  ┌─────────────────────┐.                  ┌───────────────┐
│ data-index-client ├───►│ OCS Indexer         ├───────────────────┤               │
└───────────────────┘ .  ├─────────────────────┤ batch indexation  │ Elasticsearch │
                      .  │ prepares data based │ into new index    │    Cluster    │
                      .  │ on the field config │ or updates into   │               │
                      .  └─────────────────────┘ existing index    └───────────────┘
                      .                         .                    ▲    ▲
┌───────────────────┐ .  ┌─────────────────────┐.                    │    │
│ search-client     ├─┬─►│ OCS Search Service  ├─────────────────────┘    │
└───────────────────┘ │  ├─────────────────────┤ real time requests       │
                      │  │  relies on the      │.                         │
                      │  │  prepared format    │.                         │
                      │  └─────────────────────┘.                         │
                      │                         .                         │
                      │  ┌─────────────────────┐.                         │
                      └─►│ OCS Suggest Service ├──────────────────────────┘
                      .  ├─────────────────────┤ batched read requests
                      .  │fetches data from es.│.
                      .  │indexing into local  │.
                      .  │Lucene index         │.
                      .  └─────────────────────┘.
```


1) The Indexer provides a simple API to ingest product data into Elasticsearch. Based on the configuration beneath it transforms the key-value structure into a specific document structure that is prepared for searching, facetting, sorting and ranking the results. [Check the indexer docs for more details](indexer_service.md).

2) The Search-Service relies on the document structure built by the indexer. It cares about building the Elasticsearch-Query based on the given search- and filter parameters. Similar to the indexer it moves complex decisions into the configuration. [All details about Search-Service here](search_service.md).

A preset configuration for both those services can be used for first experiments and can be used as a reference to build an adapted configuration. [Check this doc for all configuration possibilites](configuration.md).

3) Different from the first two service, the Suggest-Service is built ontop of pure Lucene, although it fetches the necessary data from Elasticsearch directly. Similar to the Search-Service it uses a "staged relaxation approach" to prefer good matches over fuzzy matches. [Read more about the details of the suggest-service here](suggest_service.md).

All those services are prepared for custom extensions. Read more about them in the [plugin guide](plugin_guide.md).

[back to top](#)

