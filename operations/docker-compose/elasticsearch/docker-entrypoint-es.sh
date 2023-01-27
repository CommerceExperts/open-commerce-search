#!/bin/bash

# install plugins here
./bin/elasticsearch-plugin install analysis-stempel

exec /usr/local/bin/docker-entrypoint.sh elasticsearch
