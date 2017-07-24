#!/bin/bash

set -e  # Exit on error

echo "Welcome to loklak Docker hub push script"

if [ "$TRAVIS_REPO_SLUG" != "loklak/loklak_server" ]; then
    echo "Skipping Docker push for repo $TRAVIS_REPO_SLUG"
    exit 0
fi

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
    echo "Skipping Docker push for pull request"
    exit 0
fi

if [ "$TRAVIS_BRANCH" != "master" -o "$TRAVIS_BRANCH" != "development" ]; then
    echo "Skipping Docker push for branch $TRAVIS_BRANCH"
    exit 0
fi

TAG_BRANCH=loklak/loklak_server:latest-$TRAVIS_BRANCH
TAG_COMMIT=loklak/loklak_server:$TRAVIS_COMMIT

echo "Logging in to Docker hub"
docker login -u $DOCKER_USERNAME -p $DOCKER_PASSWORD

docker tag loklak_server $TAG_COMMIT
docker push $TAG_COMMIT

docker tag $TAG_COMMIT $TAG_BRANCH
docker push $TAG_BRANCH

docker tag $TAG_BRANCH loklak_server
