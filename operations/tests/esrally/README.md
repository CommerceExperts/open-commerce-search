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