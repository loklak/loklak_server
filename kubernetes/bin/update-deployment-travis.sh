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

# Temporary, will be removed once we have all the deployments running
if [ "$TRAVIS_BRANCH" == "master" ]; then
    echo "No deployments for master branch"
    exit 0
fi

GC_PROJECT_STAGING=loklak-1
GC_PROJECT_API=YET-TO-DEPLOY

GC_CLUSTER=loklak-cluster
GC_ZONE=us-central1-a

TAG_STAGING=loklak/loklak_server:kubernetes-staging-$TRAVIS_COMMIT
TAG_API=loklak/loklak_server:kubernetes-api-$TRAVIS_COMMIT

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
gcloud config set compute/zone $GC_ZONE
export GOOGLE_APPLICATION_CREDENTIALS=kubernetes/gcloud-credentials.json


if [ $TRAVIS_BRANCH == "development" ]; then
    echo ">>> Updating Kubernetes deployment for staging.loklak.org"
    gcloud config set project $GC_PROJECT_STAGING
    gcloud container clusters get-credentials $GC_CLUSTER
    kubectl set image deployment/server --namespace=web server=$TAG_STAGING
fi
