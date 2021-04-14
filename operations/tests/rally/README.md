# Benchmark Elasticsearch
This folder contains all needed files and informations to create a Rally benchmark for the OCSS

## Prerequisites
- [installed docker](https://docs.docker.com/engine/install/)
- [installed docker-compose](https://docs.docker.com/compose/install/)
- [installed jq](https://stedolan.github.io/jq/download/)

## Turn on Elasticsearch query logging
The OCSS generates for every user query a Elasticsearch query, this queries are needed for the benchmark, because we want to benchmark nearest at reality as possible. For this purpose you can start the `search-service` with the Spring-Profile `trace-searches`. If this profile is set the `search-service` will write all queries to Elasticsearch into a `searches.log` file.

## Get the real Elasticsearch index name
Because in OCSS we are always operating with the Elasticsearch index alias

## Generate esrally track with your data by usig helper script
```
./create-es-rally-track.sh -i demo-olek-sven -f ../../../search-service/searches.log -o /tmp -v
```

## Run es rally track
```
esrally --distribution-version=7.7.0 --track-path=/tmp/ocss-track/
```

```
docker run -v "/tmp/ocss-track:/rally/track" --network host elastic/rally race --distribution-version=7.7.0 --track-path=/rally/track --pipeline=benchmark-only --challenge=search
```

```
docker run -v "/tmp/ocss-track:/rally/track" -v "/tmp/logs:/rally/.rally/logs" --network host elastic/rally race --track-path=/rally/track --pipeline=benchmark-only --challenge=search --target-hosts=127.0.0.1:9400
```