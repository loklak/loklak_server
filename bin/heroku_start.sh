#!/usr/bin/env bash

# Make sure we're on project root
cd $(dirname $0)/..

# Reduce Elasticsearch heap size
export ES_HEAP_SIZE=100m

exec ./bin/start.sh -Idn
