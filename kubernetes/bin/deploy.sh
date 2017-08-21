#!/bin/bash
export DIR=kubernetes

USAGE="USAGE: ./kubernetes/bin/deploy.sh  api|staging create|create all|delete|delete all"

if [[ "$1" = "api" ]]
then
    BRANCH=api
elif [[ "$1" = "staging" ]]
then
    BRANCH=staging
else
    echo "Please input correct arguments"
    echo $USAGE
    exit
fi

if [[ "$2" = "create" ]]
then
    echo "Starting '$BRANCH' branch deployment"
    echo "Creating objects from configurations."
    echo "Make sure that persistent disk is already created."
    echo ""
    echo "Creating Elasticsearch Deployment"
    kubectl create -R -f ${DIR}/yamls/${BRANCH}/elasticsearch/
    # For mqtt
    kubectl create -R -f ${DIR}/yamls/mosquitto/
    if [[ "$3" = "all" ]];
    then
        # Start KubeLego deployment for TLS certificates
        kubectl create -R -f ${DIR}/yamls/lego/
        echo "Start nginx deployment, ingress & service"
        kubectl create -R -f ${DIR}/yamls/nginx/
    fi
    # Create web namespace
    kubectl create -R -f ${DIR}/yamls/${BRANCH}/web/
    # Wait for some time to prevent any chance of API-Server to access incomplete deployments
    sleep 20
    echo "Creating Loklak Server deployment"
    # Create API server deployment and service for development branch
    kubectl create -R -f ${DIR}/yamls/${BRANCH}/api-server/
    echo "Deployed Loklak Server branch '$BRANCH' on Kubernetes"
    echo "Trying to fetch public IP. ~50s."
    sleep 50
    if [[ "$3" = "all" ]]
    then
        kubectl get services --namespace=nginx-ingress
    else
        kubectl get services --namespace=web
    fi
elif [[ "$2" = "delete" ]]
then
    echo "Clearing the cluster."
    if [ "$3" = "all" ]; then
        kubectl delete -f ${DIR}/yamls/lego/00-namespace.yml
        kubectl delete -f ${DIR}/yamls/nginx/00-namespace.yml
    fi
    kubectl delete -R -f ${DIR}/yamls/${BRANCH}/
    kubectl delete -f ${DIR}/yamls/mosquitto/00-namespace.yaml
    echo "Deleted loklak project from Kubernetes"
else
    echo "Please arguments provided!"
    echo $USAGE
fi
