#!/bin/bash
export DIR=kubernetes

USAGE="USAGE: ./kubernetes/bin/deploy-development.sh create|delete"

if [ "$1" = "create" ]; then
    echo "Creating objects from configurations."
    echo "Make sure that persistent disk is already created."
    echo ""
    echo "Creating Elasticsearch Replication Controller"
    kubectl create -R -f ${DIR}/yamls/development/elasticsearch/
    echo "Waiting for server to start up. ~20s."
    sleep 20
    echo "Creating loklak deployment"
    kubectl create -R -f ${DIR}/yamls/development/api-server/
    echo "Trying to fetch public IP. ~40s."
    sleep 40
    kubectl get services --namespace=web
    echo "Deployed loklak on Kubernetes"
elif [ "$1" = "delete" ]; then
    echo "Deleting objects created from configurations"
    kubectl delete -R -f ${DIR}/yamls/development/ || true
    echo "Deleted loklak project from Kubernetes"
elif [ -z "$1" ]; then
    echo "No arguments provided"
    echo $USAGE
fi
