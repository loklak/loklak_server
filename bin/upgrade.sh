#!/usr/bin/env sh
cd `dirname $0`/..
echo "loading latest code changes"
git pull -r
echo "clean up"
./gradlew clean
echo "building loklak"
./gradlew build
bin/restart.sh
