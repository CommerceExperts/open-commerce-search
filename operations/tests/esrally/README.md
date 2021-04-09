# Creating and running Elasticsearch perfomance test
## Requirements

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