#!/bin/bash

if [[ "$1" = "api" ]]
then
    BRANCH=Api
    IMAGE=loklak/loklak_server:latest-kubernetes-api
elif [[ "$1" = "staging" ]]
then
    BRANCH=Staging
    IMAGE=loklak/loklak_server:latest-kubernetes-staging
else
    IMAGE=$1
fi

echo "Setting image $IMAGE"

if [[ "$1" = "staging" ]]
then
    kubectl set image deployment/server --namespace=api-staging server=$IMAGE
else
    kubectl set image deployment/server --namespace=api-web server=$IMAGE
fi

echo "Succesfully updated $BRANCH image"
