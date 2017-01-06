#!/usr/bin/env bash

# Make sure we're on project root
cd $(dirname $0)/..

eval ./bin/start.sh -Id

tail -f data/loklak.log
