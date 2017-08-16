#!/bin/bash
export DIR=kubernetes

USAGE="USAGE: ./kubernetes/bin/deploy-development.sh create|create all|delete|delete all"

if [[ "$1" = "create" ]];
then
    echo "Creating objects from configurations."
    echo "Make sure that persistent disk is already created."
    echo ""
    echo "Creating Elasticsearch Deployment"
        kubectl create -R -f ${DIR}/yamls/staging/elasticsearch/
    # For mqtt
        kubectl create -R -f ${DIR}/yamls/mosquitto/
    if [[ "$2" = "all" ]];
    then
        # Start KubeLego deployment for TLS certificates
        kubectl create -R -f ${DIR}/yamls/lego/
        echo "Start nginx deployment, ingress & service"
        kubectl create -R -f ${DIR}/yamls/nginx/
    fi
    # Create web namespace
    kubectl create -R -f ${DIR}/yamls/staging/web/
    # Wait for some time to prevent any chance of API-Server to access incomplete deployments
    sleep 20
    echo "Creating loklak deployment"
    # Create API server deployment and service for development branch
    kubectl create -R -f ${DIR}/yamls/staging/api-server/
    echo "Deployed loklak on Kubernetes"
    echo "Trying to fetch public IP. ~50s."
    sleep 50
    if [[ "$2" = "all" ]];
    then
        kubectl get services --namespace=nginx-ingress
    fi
    else
        kubectl get services --namespace=web
    fi
if [[ "$1" = "delete" ]];
then
    echo "Clearing the cluster."
    if [ "$2" = "all" ]; then
        kubectl delete -f ${DIR}/yamls/lego/00-namespace.yml
        kubectl delete -f ${DIR}/yamls/nginx/00-namespace.yml
    fi
    kubectl delete -R -f ${DIR}/yamls/staging/
    kubectl delete -f ${DIR}/yamls/mosquitto/00-namespace.yaml
    echo "Deleted loklak project from Kubernetes"
elif [[ -z "$1" ]];
then
    echo "No arguments provided"
    echo $USAGE
fi
