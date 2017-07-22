# Deploying `development` version of loklak on Kubernetes

## 1. Background of the Deployment

### 1.1. API Server and Elasticsearch
The API server and Elasticsearch would co exist in the `web` namespace. API Server would use `NodeBuilder` to create a Node based Elasticsearch cluster with dump and index at `/loklak_server/data` volume.

### 1.2. Persistent Storage for Data Dump and Elasticsearch Index
The data dump and Elasticsearch index would be mounted on external persistent disk so that rolling updates do not wipe out the data.

## 2. Steps

### 2.1. Create a Kubernetes cluster

Kubernetes cluster can be created easily using the `gcloud` CLI. More details about this are available [here](https://github.com/loklak/loklak_server/blob/development/docs/installation/installation_google_cloud_kubernetes.md#7-creating-a-container-cluster).

### 2.2. Clone the loklak project

```bash
git clone https://github.com/loklak/lokklak_server
cd loklak_server
```

Ensure that you are at `development` branch.

```bash
git checkout development
```

### 2.3. Create a Persistent Disk

```bash
gcloud compute disks create --size=100GB --zone=<same as cluster zone> data-index-disk
```

### 2.4. Create Kubernets objects using configuration files

```bash
./kubernetes/bin/deploy-development.sh create
```

### 2.5. Label the Node

The persistent disk is attached to the node running `api-server`. But in case of rolling update, new container may get created on a different node.

This would result in issues as the new instance would now try to mount a HDD which is already in use by another (older) instance.

To avoid this, we enforce the new containers get created on same instance by labeling them. A selector for this is already present in the `api-server` deployment configuration.

You can get the node name by running

```bash
kubectl get nodes --namespace=web
```

Choose one of the nodes and label it as `server=primary`.

```bash
kubectl label nodes <node-name> server=primary
```

## 3. Modifying deployment

### 3.1. Setting a new Docker image

To update deployment image, we can use the following command -

```bash
./kubernetes/bin/update-development-image.sh <image name>  # Defaults to loklak/loklak_server:development
```

### 3.2. Updating configurations

While updating the configurations, it should be ensured that the `api-service.yml` configuration is not recreated. This would retain previous IP and save us from the trouble of updating DNS.

### 3.3. Deleting deployment

```bash
./kubernetes/bin/deploy-development.sh delete
```
