#!/bin/bash

if [ -e docker-compose.custom.yml ]; then
    CUSTOM_YML_PARAM="-f docker-compose.custom.yml"
fi

docker compose -f docker-compose.base.yml -f docker-compose.local.yml $CUSTOM_YML_PARAM "$@"
