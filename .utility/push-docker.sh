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

if [ "$TRAVIS_BRANCH" != "master" ] && [ "$TRAVIS_BRANCH" != "development" ]; then
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

# Build and push Kubernetes Docker image
KUBERNETES_BRANCH=loklak/loklak_server:latest-kubernetes-$TRAVIS_BRANCH
KUBERNETES_COMMIT=loklak/loklak_server:kubernetes-$TRAVIS_COMMIT

if [ "$TRAVIS_BRANCH" == "development" ]; then
    docker build -t loklak_server_kubernetes kubernetes/images/development
    docker tag loklak_server_kubernetes $KUBERNETES_BRANCH
    docker push $KUBERNETES_BRANCH
    docker tag $KUBERNETES_BRANCH $KUBERNETES_COMMIT
    docker push $KUBERNETES_COMMIT
elif [ "$TRAVIS_BRANCH" == "master" ]; then
    # Build and push master
else
    echo "Skipping Kubenetes image push for branch $TRAVIS_BRANCH"
fi
