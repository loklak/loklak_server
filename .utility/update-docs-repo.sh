#!/bin/sh

# dev.loklak.org Update trigger script

# This script is used to trigger the update process
# of dev.loklak.org

WORK="$HOME/central-docs"
REPO="loklak/dev.loklak.org"
PRIVATE_KEY="$(pwd)/.utility/loklakserver.2"

echo "Preparing ssh agent ..."
eval $(ssh-agent -s)
ssh-add $PRIVATE_KEY

echo "Preparing git ..."
git config --global user.email "travis@travis-ci.org"
git config --global user.name "travis-ci"

echo "Cloning repository ..."
git clone --quiet --branch=gh-pages "git@github.com:$REPO.git" "$WORK"
cd "$WORK"

echo "Running pull script ..."
./.ci/pull.sh config.csv

echo "Pushing changes ..."
git push -f origin gh-pages
