# Deploy Loklak-Server docker image on Kubernetes Cluster Bluemix

## Pre-Requisites

1. Signup for a Bluemix Account [here](https://www.ibm.com/cloud-computing/bluemix/)

2. Setup Docker on your local machine. Signup at hubs.docker.com , link github with docker and create Repository for your fork Loklak-Server.
    Linux:
    ```
    Debian: apt-get install docker
    RPM: dnf install docker
    ```
    Note: the docker repository shall be public

3. Download and install cloud foundry command line tools for your OS from [here](https://github.com/cloudfoundry/cli/releases)
    Linux:
    ```
    dpkg -i cf-cli-version.dep
    ```

4. Install ic plugin as described [here](https://www.ng.bluemix.net/docs/containers/container_cli_ov.html#container_cli_cfic_install)
    Linux:
    ```
    wget https://www.ng.bluemix.net/docs/containers/container_cli_ov.html#container_cli_cfic_install
    cf install-plugin https://static-ice.ng.bluemix.net/ibm-containers-linux_x64
    ```

## Login and settings

5. Login to any region (US South, Sydney or United Kingdom), setup Organization and Space for it.
    ```
    Region:         United Kingdom
    User:           vibhorverma1995@gmail.com
    Org:            BAAC
    Space:          loklak-server
    ```

6. Login to bluemix with api endpoint.
    ```
    cf login -a <API endpoint>
    ```
   API endpoint for United Kingdom is `https://api.eu-gb.bluemix.net`

7. Login/initiate ibm container plugin.
    ```
    cf ic login
    ```
    or
    ```
    cf ic init
    ```

8. Create namespace with unique name like vibhcool.
    ```
    cf ic namespace set <your unique namespace>
    ```

## Setup

9. Upload the loklak docker file to your namespace. There are 2 ways to do this:

    a. Upload docker image from your local machine. For docker installation, see [installation_docker.md](https://github.com/loklak/loklak_server/blob/development/docs/installation/installation_docker.md)
    ```
    docker tag <docker image id> <registry_name>/<your namespace>/<bluemix space name>:<tag_name>
    docker push <registry_name>/<your namespace>/<bluemix space name>:<tag_name>
    ```
    Like:
    ```
    docker tag b4de28726243 registry.eu-gb.bluemix.net/vibhcool/loklak_server:v1
    docker push registry.eu-gb.bluemix.net/vibhcool/loklak_server:v1
    ```

    b. Copy docker image from docker public repository.
    ```
    cf ic cpi <docker-username>/<docker public repo> <registry_name>/<your namespace>/<bluemix space name>:<tag_name>
    ```
    Like:
    ```
    cf ic cpi vibhcool/loklak_server registry.eu-gb.bluemix.net/vibhcool/loklak_server:v1
    ```

10. Create Kubernetes Cluster with image uploaded to your private bluemix registry. There are 2 ways:

    a. Go to [catalog](https://console.bluemix.net/catalog/?category=containers) option on your bluemix dashboard and select Container category. Select Kubernetes cluster, select image, create the instance group (preferably Scalable cluster over Single cluster)

    b. Or on terminal:
    ```
    cf ic group create --name loklak --auto --desired 2 -m 1024 -n cloud-loklak -d mybluemix.net -p 80 registry.eu-gb.bluemix.net/vibhcool/loklak_server:v1
    ```

11. Check if your group is running either with pressing Dashboard in the browser or on terminal:
    ```
    cf ic group list
    ```

12. Wait until your container group is built and the network is configured (>1 minute) and then check at your public IP (Single instance) or route (scalable instance group) that is assigned by bluemix.
