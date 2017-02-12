#!/bin/bash

# Loklak Automatic Deployer

# This script is made to automatically update and redeploy Loklak.

# NOTE: Before you run this, please make sure $SERVICE_DIRECTORY is created
#       and it's owned by $USER_TO_USE

SERVICE_DIRECTORY="/opt/loklak"
GIT_REPO_DIRECTORY="$SERVICE_DIRECTORY/loklak/repo"
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

cd $SERVICE_DIRECTORY

puts "Updating containers ..."

docker-compose up --force-recreate --remove-orphans --build -d
