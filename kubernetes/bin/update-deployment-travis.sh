#!/bin/bash

set -e

if [ "$TRAVIS_REPO_SLUG" != "loklak/loklak_server" ]; then
    echo "Skipping image update for repo $TRAVIS_REPO_SLUG"
    exit 0
fi

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
    echo "Skipping image update for pull request"
    exit 0
fi

if [ "$TRAVIS_BRANCH" != "master" ] && [ "$TRAVIS_BRANCH" != "development" ]; then
    echo "Skipping image update for branch $TRAVIS_BRANCH"
    exit 0
fi

GC_PROJECT=loklak-1
GC_CLUSTER=loklak-cluster
TAG=loklak/loklak_server:kubernetes-$TRAVIS_COMMIT

echo ">>> Decrypting credentials"
openssl aes-256-cbc -K $encrypted_48d01dc243a6_key -iv $encrypted_48d01dc243a6_iv  -in kubernetes/gcloud-credentials.json.enc -out kubernetes/gcloud-credentials.json -d

echo ">>> Installing Google Cloud SDK with Kubernetes"
export CLOUDSDK_CORE_DISABLE_PROMPTS=1
curl https://sdk.cloud.google.com | bash > /dev/null
source ~/google-cloud-sdk/path.bash.inc
gcloud components install kubectl

echo ">>> Authenticating Google Cloud using decrypted credentials"
gcloud auth activate-service-account --key-file kubernetes/gcloud-credentials.json

echo ">>> Configuring Google Cloud"
gcloud config set compute/zone us-central1-a
export GOOGLE_APPLICATION_CREDENTIALS=kubernetes/gcloud-credentials.json
gcloud config set project $GC_PROJECT
gcloud container clusters get-credentials $GC_CLUSTER

echo ">>> Updating Kubernetes deployment"
if [ $TRAVIS_BRANCH == "development" ]; then
    kubectl set image deployment/server --namespace=web server=$TAG
fi
