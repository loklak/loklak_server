#!/usr/bin/env sh
cd $(dirname $0)/..

# Execute preload script
source bin/.preload.sh

echo "starting Tests"

java -Xmx1024m -classpath .:build/libs/loklak_server-all.jar:build/classes/test org.junit.runner.JUnitCore org.loklak.TestRunner
