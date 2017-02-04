#!/bin/bash

# Loklak Automatic Deployer

# This script is made to automatically update and redeploy Loklak.

# NOTE: Before you run this, please make sure $SERVICE_DIRECTORY is created
#       and it's owned by $USER_TO_USE

SERVICE_DIRECTORY="/opt/loklak"
GIT_REPO_DIRECTORY="$SERVICE_DIRECTORY/git-repo"
DATA_DIRECTORY="$SERVICE_DIRECTORY/data"
DOCKER_CONTAINER_NAME="loklak_container"
ELASTIC_DOCKER_CONTAINER_NAME="elasticsearch"
USER_TO_USE="loklak"

UID_TO_USE="$(id -u $USER_TO_USE)"
GID_TO_USE="$(id -g $USER_TO_USE)"

PREFIX="\033[1;34m>> "
SUFFIX="\033[0m"

function puts {
    echo -e "$PREFIX\033[1;32m$@$SUFFIX"
}

function fail {
    echo -e "$PREFIX\033[1;31m$@$SUFFIX"
    exit 1
}

function remove_container {
    puts "Removing Loklak Docker container ..."

    if ! docker rm -f "$DOCKER_CONTAINER_NAME"; then
        fail "Failed removing Loklak Docker container. Exiting ..."
    fi
}

function stop_container {
    puts "Stopping Loklak server ..."

    if ! docker exec "$DOCKER_CONTAINER_NAME" /loklak_server/bin/stop.sh; then
        fail "Failed stopping Loklak server. Exiting ..."
    fi

    remove_container
}


puts "Welcome to Loklak Automatic Deployer"

echo

puts "Checking credentials ..."

if [[ "$(whoami)" != "$USER_TO_USE" ]]; then
    fail "Invalid account is used. Exiting ..."
fi

# Go to the repo directory
cd "$GIT_REPO_DIRECTORY"

puts "Pulling new changes ..."

if ! git pull; then
    fail "Failed pulling new changes. Exiting ..."
fi

puts "Building Docker image ..."

if ! docker build -q -t loklak -f docker/Dockerfile .; then
    fail "Failed building Docker image. Exiting ..."
fi


puts "Checking for existing container(s) ..."

# Check if there's a running container
if docker ps | grep "$DOCKER_CONTAINER_NAME"; then
    puts "Found running container!"
    stop_container
# Check if there's a dead container
elif docker ps -a | grep "$DOCKER_CONTAINER_NAME"; then
    puts "Found dead container!"
    remove_container
fi

# Make data folder, just in case
mkdir -p "$DATA_DIRECTORY"

puts "Starting Loklak Docker container ..."

docker run -d -v "$DATA_DIRECTORY":"/loklak_server/data" --name "$DOCKER_CONTAINER_NAME" -p 80:80 -p 443:443 --link "$ELASTIC_DOCKER_CONTAINER_NAME" --restart always loklak

if [[ ! $? -eq 0 ]] ; then
    fail "Failed starting Loklak Docker container. Exiting ..."
fi

puts "Latest version of Loklak server is deployed!"
