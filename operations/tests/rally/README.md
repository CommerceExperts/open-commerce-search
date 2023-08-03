# Benchmark Elasticsearch
This folder contains all needed files and information to create a [Rally]((https://esrally.readthedocs.io/en/stable/)) benchmark for the OCSS.

## Prerequisites
- [installed docker](https://docs.docker.com/engine/install/)
- [installed docker-compose](https://docs.docker.com/compose/install/)
- [installed jq](https://stedolan.github.io/jq/download/)

## Turn on Elasticsearch query logging
The OCSS generates for every user query an Elasticsearch query, these queries are needed for the benchmark because we want to benchmark nearest as reality as possible. For this purpose you can start the `search-service` with the Spring-Profile `trace-searches`. If this profile is set the `search-service` will write all queries to Elasticsearch into a `searches.log` file. Have a look [here](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto-set-active-spring-profiles) at how you can set a Spring-Profile.

## Get the real Elasticsearch index name
Because in OCSS we are always operating with the Elasticsearch index alias, we first have to get the real `index name` behind the `alias` we want to use for benchmarking. This can be done by using the Elasticsearch `_alias` endpoint. Following a sample were Elasticsearch is running on `localhost:9200`:
```
# curl -s http://localhost:9200/_aliases | jq .
{
  "ocs-1-demo-index": {
    "aliases": {
      "demo-index": {}
    }
  }
}
```
Of course, this can also be done over Kibana, by making a `GET` on `/_aliases`.

## Generate esrally track with your data by usig helper script 
After we have the `index name` and `searches.log` we are ready to use the [./create-es-rally-track.sh](./create-es-rally-track.sh) to generate the track. Notice: The script needs to connect to your Elasticsearch.
```
# ./create-es-rally-track.sh -i ocs-1-demo-index -f ../../../search-service/searches.log -o /tmp -s 127.0.0.1:9200 -v
Creating output dir /tmp ...
Output dir /tmp created.
Creating rally data from index ocs-1-demo-index ...

    ____        ____
   / __ \____ _/ / /_  __
  / /_/ / __ `/ / / / / /
 / _, _/ /_/ / / / /_/ /
/_/ |_|\__,_/_/_/\__, /
                /____/

[INFO] Connected to Elasticsearch cluster [ocs-es-default-1] version [7.5.2].

Extracting documents for index [ocs-1-demo-index]...       1001/1000 docs [100.1% done]
Extracting documents for index [ocs-1-demo-index]...       2255/2255 docs [100.0% done]

[INFO] Track ocss-track has been created. Run it with: esrally --track-path=/tracks/ocss-track

--------------------------------
[INFO] SUCCESS (took 25 seconds)
--------------------------------
Rally data from index ocs-1-blog in /tmp created.
Manipulate generated /tmp/ocss-track/track.json ...
Manipulated generated /tmp/ocss-track/track.json.
Start with generating challenges...
Challenges from search log created.
```
The script creates the `track` in the folder `/tmp/ocss-track`. If you are not familiar with what a `track` is, take a look at our [blog](https://blog.searchhub.io/how-to-setup-elasticsearch-benchmarking) or check the official [Rally documentation](https://esrally.readthedocs.io/en/stable/).
The generated `track` has three `challenges`:

 - index
 - search
 - search-while-index


## Run a challenge without storing metrics
The following command will start a single node Elasticsearch locally and run the benchmark, this is just an example of the track. If you want to Benchmark a whole cluster please have a look at the official documentation [here](https://esrally.readthedocs.io/en/stable/cluster_management.html).

### run index challenge:
```
docker run -v "/tmp/ocss-track:/rally/track" elastic/rally race --distribution-version=7.9.2 --track-path=/rally/track --pipeline=benchmark-only --challenge=index
```
### run search challenge:
```
docker run -v "/tmp/ocss-track:/rally/track" elastic/rally race --distribution-version=7.9.2 --track-path=/rally/track --pipeline=benchmark-only --challenge=search
```
### run search-while-index challenge:
```
docker run -v "/tmp/ocss-track:/rally/track" elastic/rally race --distribution-version=7.9.2 --track-path=/rally/track --pipeline=benchmark-only --challenge=search-while-index
```

## Run a challenge with storing metrics in Kibana
With Rally it is possible to store the metrics generated while the benchmark in an Elasticsearch instance so that you can analyze them with Kibana. This has to be configured in the [rally.ini](rally.ini) file and is in our track preconfigured for an Elasticsearch running on `localhost`. We provide the [docker-compose-results.yaml](docker-compose-results.yaml) so that you can start the stack easily with the following command:
```
# docker-compose -f docker-compose-results.yaml up -d
```
If the stack is up and running, you can start the challenges with the following commands:
### run index challenge:
```
docker run -v "/tmp/ocss-track:/rally/track" -v "/tmp/ocss-track/rally.ini:/rally/.rally/rally.ini" --network host elastic/rally race --distribution-version=7.9.2 --track-path=/rally/track --pipeline=benchmark-only --challenge=index --race-id=index
```
### run search challenge:
```
docker run -v "/tmp/ocss-track:/rally/track" -v "/tmp/ocss-track/rally.ini:/rally/.rally/rally.ini" --network host elastic/rally race --distribution-version=7.9.2 --track-path=/rally/track --pipeline=benchmark-only --challenge=search --race-id=search
```
### run search-while-index challenge:
```
docker run -v "/tmp/ocss-track:/rally/track" -v "/tmp/ocss-track/rally.ini:/rally/.rally/rally.ini" --network host elastic/rally race --distribution-version=7.9.2 --track-path=/rally/track --pipeline=benchmark-only --challenge=search-while-index --race-id=search-while-index
```
To analyze the results, you can now visit `localhost:5601` and create your own dashboard or use some of the Internet like [this one](https://github.com/Abmun/rally-apm-search/blob/master/Rally-Results-Dashboard.ndjson) or [that one](https://github.com/Abmun/rally-apm-search/blob/master/Rally-Results-Dashboard.ndjson).
