[Home](./) > [Quick Start Demo](./quick_start_demo.md)

## Table of Contents

- [Quick Start Demo](#quick-start-demo)
  - [Prerequisites](#prerequisites)
  - [Start the stack](#start-the-stack)
    - [indexer](#indexer)
    - [searcher](#searcher)
    - [elasticsearch](#elasticsearch)
  - [Index data](#index-data)
  - [Use search-api](#use-search-api)


# Quick Start Demo
This guide gives you an short overview, how to start OCSS locally and index sample data from [kaggle](https://www.kaggle.com/). This could be usefull for example for frontend developers who want to develop their frontend against the search api. This guide is NOT for how to configure OCSS the best way etc.

## Prerequisites
- [installed docker](https://docs.docker.com/engine/install/)
- [installed docker-compose](https://docs.docker.com/compose/install/)
- [installed jq](https://stedolan.github.io/jq/download/)
- [sample dataset](https://www.kaggle.com/gpreda/reddit-vaccine-myths) from [kaggle](https://www.kaggle.com/)

## Start the stack
Before we can index some data to OCSS we have to start the stack. For this purpose there is an [docker-compose folder](https://github.com/CommerceExperts/open-commerce-search/tree/master/operations/docker-compose) in the operations folder of this repository. This folder contains files needed to start the stack locally:

- docker-compose.yml: defines the services which should be started
- application.indexer-service.yml: configures the indexer spring boot application and the defines the search configuration
- application.search-service.yml: configures the search spring boot application

The [docker-compose.yml](https://github.com/CommerceExperts/open-commerce-search/tree/master/operations/docker-compose/docker-compose.yml) defines 3 services:
 - indexer
 - searcher
 - elasticsearch
 
### indexer
The indexer service is transforming and indexing the data into elasticsearch. Here is handled that for exmaple the rigth master/child structure is created out of the product data etc. The service provides REST endpoints though an normal indexing process. This means you can create an index, add product data, and set the indexing state to done over a simple API.

### searcher
The search service is performing the search / filtering etc. on top of the search engine. In the repo this is done on top of elasticsearch as reference implementation.

### elasticsearch
Elasticsearch in this case is the searchengine we used for our reference implementation of the OCSS search and indexing API.

To start the stack change the parent working direktory next to the [docker-compose.yml](https://github.com/CommerceExperts/open-commerce-search/tree/master/operations/docker-compose/docker-compose.yml) and perform the following command:
```
# docker-compose up -d
Starting ocs_elasticsearch ... done
Starting ocs_searcher      ... done
Starting ocs_indexer       ... done
```
This command will startup all services defined in the [docker-compose.yml](https://github.com/CommerceExperts/open-commerce-search/tree/master/operations/docker-compose/docker-compose.yml). To confirm that all three services are up and running you can use the `docker ps` command:
```
# docker ps
CONTAINER ID        IMAGE                                                 COMMAND                  CREATED             STATUS              PORTS                              NAMES
e367877eb384        commerceexperts/ocs-indexer-service:latest            "java -server -cp /a…"   4 minutes ago       Up 40 seconds       0.0.0.0:8535->8535/tcp             ocs_indexer
ebaa75aa2a20        commerceexperts/ocs-search-service:latest             "java -server -cp /a…"   4 minutes ago       Up 39 seconds       0.0.0.0:8534->8534/tcp             ocs_searcher
433e52146c9c        docker.elastic.co/elasticsearch/elasticsearch:7.9.2   "/tini -- /usr/local…"   4 minutes ago       Up 41 seconds       0.0.0.0:9200->9200/tcp, 9300/tcp   ocs_elasticsearch
```
As you can see there are now 3 sockets opened:
 - `http://localhost:8535/` (Index-API endpoint)
 - `http://localhost:8534/` (Search-API endpoint)
 - `http://localhost:9200/` (Elasticsearch endpoint)


[back to top](#)

## Index data
For indexing data from a CSV file there is a helper script [ocs-index-data.sh](https://github.com/CommerceExperts/open-commerce-search/tree/master/operations/ocs-index-data.sh). After we downloaded the sample dataset from kaggle, we have to unpack the `.zip` file to get the `reddit_vm.csv`. Now let us inspect the CSV file to get an overview of the data. In the first step the `field delimiter`, the `field sperator`, the `count of header rows` and the `id field number` (starting from 0) are intressting for us. To get this information just print the first two lines of the CSV file:
```
# head -n 2 reddit_vm.csv
title,score,id,url,comms_num,created,body,timestamp
Health Canada approves AstraZeneca COVID-19 vaccine,7,lt74vw,https://www.canadaforums.ca/2021/02/health-canada-approves-astrazeneca.html,0,1614400425.0,,2021-02-27 06:33:45
```

*The sample dataset contains a collection of vaccine myths posted on [the subreddit r/VaccineMyths](https://www.reddit.com/r/VaccineMyths/).*

Here we can get our wanted informations:
- field delimiter => `","`
- field separator => `""` (not present)
- header row count => `"1"`
- id field number => `"2"`

Let's call the script to look what we get (For more options call the script with -h to get an overview):
```
# ./ocs-index-data.sh -f /tmp/reddit_vm.csv -s "," -q "" -k 1 -i 2 -n quick-start -l en
ERROR: no mapping defined. These are the columns of the given file:
     0	title
     1	score
     2	id
     3	url
     4	comms_num
     5	created
     6	body
     7	timestamp
```

The script tells us now that we have to describe how the single colmuns of the CSV file should be mapped to search configuration. This should be done the [jq](https://stedolan.github.io/jq/tutorial/) way, to make this easier for us the script is telling us the column numbers of the headers. As we don't have a field configuration yet, we have to create one. As described above this can be done in the [application.indexer-service.yml](https://github.com/CommerceExperts/open-commerce-search/tree/master/operations/docker-compose/application.indexer-service.yml). The following search field configuration (complete application.indexer-service.yml) would fit to our datafeed:
```
spring:
  logging:
    level:
      de.cxp.ocs: DEBUG

ocs:
  index-config:
    quick-start:
      field-configuration:
        fields:
          id:
            name: id
            type: id
            usage:
              - Result
          title:
            name: title
            type: string
            usage:
              - Search
              - Result
              - Sort
          score:
            name: score
            type: number
            usage:
              - Facet
              - Result
              - Sort
          url:
            name: url
            type: string
            usage:
              - Result
          number_of_comments:
            name: number_of_comments
            type: string
            source-names:
              - comms_num
            usage:
              - Result
              - Facet
              - Sort
          created:
            name: created
            type: number
            usage:
              - Result
              - Sort
          comment:
            name: comment
            source-names:
              - body 
            usage:
              - Search
              - Result
          timestamp:
            name: timestamp
            usage:
              - Result
```

Please notice that if you change the search configuration you have to restart the indexer service. This could be done with the following command next to the docker-compose.yml:
```
# docker-compose restart indexer
Restarting ocs_indexer ... done
```

Once we have the field configuration the mapping for the [ocs-index-data.sh](https://github.com/CommerceExperts/open-commerce-search/tree/master/operations/ocs-index-data.sh) is clear and the parameter value in our case would be:
```
'{title:.[0],score:.[1],url:.[3],comms_num:.[4],created:.[5],body:.[6],timestamp:.[7]}'
```

Okay, so let's run the script again and index the data (the indexing process can take a while):
```
# ./ocs-index-data.sh -f /tmp/reddit_vm.csv -s "," -q "" -k 1 -i 2 -m '{title:.[0],score:.[1],url:.[3],comms_num:.[4],created:.[5],body:.[6],timestamp:.[7]}' -n quick-start -l en -v
```

After the indexing process is done, check elasticsearch if the index is there:
```
# curl http://localhost:9200/_cat/indices                       
green open ocs-2-quick-start-en mH0OHNsVS5K4L3yvp_gqaA 1 0 3937 2256 1.9mb 1.9mb
```

As we can see, the index creation and indexing was successful.

[back to top](#)

## Use search-api

After we created an index, it's time to try a search against the search api.

```
# curl "http://localhost:8534/search-api/v1/search/quick-start?q=beer" | jq .
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100 10288    0 10288    0     0   401k      0 --:--:-- --:--:-- --:--:--  401k
{
  "tookInMillis": 19,
  "inputURI": "",
  "slices": [
    {
      "label": null,
      "matchCount": 1323,
      "nextOffset": 12,
      "nextLink": null,
      "resultLink": "",
      "hits": [
        {
          "index": "ocs-2-quick-start-en",
          "document": {
            "id": "lt67lb",
            "data": {
              "score": "1",
              "search_combi": "Beer after corona vaccination",
              "created": "1614397967.0",
              "comment": "\"Hello hello people",
              "title": "Beer after corona vaccination",
              "number_of_comments": "0",
              "url": "https://www.reddit.com/r/VaccineMyths/comments/lt67lb/beer_after_corona_vaccination/"
            },
            "attributes": null,
            "categories": null
          },
          "matchedQueries": [
            "_match_all"
          ]
          ...
```


[back to top](#)
