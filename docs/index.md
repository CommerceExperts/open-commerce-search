
## Contents
- [Overview](#overview)
- [Quick Start](#quick-start)
- [Generated API Doc](#generated-API-doc)
- [Operations](#operations)
  - [Kubernetes](#kubernetes)
  - [Tests](#tests)
    - [Rally Benchmark](#rally-Benchmark)

## Overview

The Open Commerce Search Stack (OCSS) is a small abstraction layer on top of existing open source search solutions (Elasticsearch, Lucene and Querqy) that removes the hastle of dealing with the details of information retrieval. It was designed to handle most problems of search in e-commerce using known best practices. 
The API first approach aims for simplicity and broad adaption.
[More information about the motivation and background of OCSS can be found in our blog](https://blog.searchhub.io/introducing-open-commerce-search-stack-ocss).


The OCS-Stack is devided into 3 services: Indexer, Search- and Suggest-Service:

1) The Indexer provides a simple API to ingest product data into Elasticsearch. Based on the configuration beneath it transforms the key-value structure into a specific document structure that is prepared for searching, facetting, sorting and ranking the results. [Check the indexer docs for more details](indexer_service.md).

2) The Search-Service relies on the document structure built by the indexer. It cares about building the Elasticsearch-Query based on the given search- and filter parameters. Similar to the indexer it moves complex decisions into the configuration. [All details about Search-Service here](search_service.md).

A preset configuration for both those services can be used for first experiments and can be used as a reference to build an adapted configuration. [Check this doc for all configuration possibilites](configuration.md).

3) Different from the first two service, the Suggest-Service is built ontop of pure Lucene, although it fetches the necessary data from Elasticsearch directly. Similar to the Search-Service it uses a "staged relaxation approach" to prefer good matches over fuzzy matches. [Read more about the details of the suggest-service here](suggest_service.md).

All those services are prepared for custom extensions. Read more about them in the [plugin guide](plugin_guide.md).


## Quick start
Please have a look at our [quick_start_demo.md](quick_start_demo.md). This short tutorial helps you to get the OCSS up and running locally with some sample data.

## Generated API doc
If you want to get an overview over the complete open-commerce-search-api have a look at [generated_api-doc.md](generated_api_doc.md). This documentation is generated out of the OpenAPI definition under [open-commerce-search-api/src/main/resources/openapi.yaml](open-commerce-search-api/src/main/resources/openapi.yaml) with the help of [widdershins](https://github.com/Mermade/widdershins).

## Operations
### Kubernetes
If you have to run the OCSS in kubernetes, you can take a look at the [README.md](operations/k8s/README.md) in the [operations/k8s/](operations/k8s/) folder.

### Tests
#### Elasticsearch Bechmark
Because the reference implementation of the OCS-API is done with Elasticsearch as search backend we are providing a way to benchmark Elasticsearch with your data. Please have a look at [operations/tests/rally/README.md](operations/tests/rally/README.md) if you want to know more about this.
