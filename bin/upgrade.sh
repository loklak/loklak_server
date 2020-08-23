#!/usr/bin/env sh
cd `dirname $0`/..
echo "loading latest code changes"
git pull -r
echo "assembling loklak"
./gradlew assemble
bin/restart.sh
