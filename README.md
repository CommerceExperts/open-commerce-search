# Open-Commerce-Search-Stack (OCSS)
Framework for building Commerce Search Solutions around open source search technology like Elasticsearch.

## Contents
- [Introduction](#open-Commerce-Search-Stack-(OCSS))
- [Overview](#overview)
- [Docs](#docs)
  - [Quick Start](#quick-start)
  - [Generated API Doc](#generated-API-doc)
- [Operations](#operations)
  - [Kubernetes](#kubernetes)
  - [Tests](#tests)
    - [Rally Benchmark](#rally-Benchmark)

## Overview

## Docs
### Quick start
Please have a look at our [quick_start_demo.md](docs/quick_start_demo.md). This short tutorial helps you to get the OCSS up and running locally with some sample data.

### Generated API doc
If you want to get an overview over the complete open-commerce-api have a look at [generated_api-doc.md](docs/generated_api_doc.md). This documentation is generated out of the OpenAPI definition under [open-commerce-search-api/src/main/resources/openapi.yaml](open-commerce-search-api/src/main/resources/openapi.yaml) with the help of [widdershins](https://github.com/Mermade/widdershins).

## Operations
### Kubernetes
If you have to run the OCSS in kubernetes, you can take a look at the [README.md](operations/k8s/README.md) in the [operations/k8s/](operations/k8s/) folder.

### Tests
#### Elasticsearch Bechmark
Because the reference implementation of the OCS-API is done with Elasticsearch as search backend we are providing a way to benchmark Elasticsearch with your data. Please have a look at [operations/tests/rally/README.md](operations/tests/rally/README.md) if you want to know more about this.