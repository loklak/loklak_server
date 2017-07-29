#!/bin/bash
export DIR=kubernetes

if [ "$1" = "create" ]; then
    echo "Creating objects from configurations."
    echo "Make sure that persistent disk is already created."
    kubectl create -R -f ${DIR}/yamls/development/
    echo "Waiting for server to start up. ~1m."
    sleep 60
    echo "Trying to fetch public IP"
    kubectl get services --namespace=web
    echo "Deployed loklak on Kubernetes"
elif [ "$1" = "delete" ]; then
    echo "Deleting objects created from configurations"
    kubectl delete -R -f ${DIR}/yamls/development/
    echo "Deleted loklak project from Kubernetes"
fi
