#!/bin/bash

cp README.rst $HOME/.

cd $HOME
git config --global user.email "travis@travis-ci.org"
git config --global user.name "travis-ci"
git clone --quiet --branch=gh-pages git@github.com:loklak/dev.loklak.org.git gh-pages

cd gh-pages
mkdir -p raw
cd raw
cp -f $HOME/README.rst ./server.rst
git add -f .
git commit -m "Latest server documentation file on successful travis build $TRAVIS_BUILD_NUMBER auto-pushed to dev.loklak.org"
git push -fq origin gh-pages > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo -e "Published Raw file to dev.loklak.org.\n"
    exit 0
else
    echo -e "Publishing failed. Maybe the access-token was invalid or had insufficient permissions.\n"
    exit 1
fi
