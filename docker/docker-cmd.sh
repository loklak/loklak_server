#!/bin/bash

cd $(dirname $0)/..

bin/start.sh -I

tail -f data/loklak.log
