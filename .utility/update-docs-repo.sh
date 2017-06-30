#!/bin/bash

export LOC=$(pwd)
PRIVATE_KEY="$(pwd)/.utility/loklakserver.2"

eval $(ssh-agent -s)
ssh-add $PRIVATE_KEY

cp README.rst $HOME/.

git config --global user.email "travis@travis-ci.org"
git config --global user.name "travis-ci"

git subtree --prefix=docs/ split -b documentation

cd $HOME

git clone --branch=master git@github.com:loklak/dev.loklak.org.git loklak_docs

cd loklak_docs

echo "Pulling subtree..."
if git subtree pull --prefix=raw/server $LOC documentation --squash -m "Update server subtree" ; then
  echo "Pulled successfully."
else
  echo "Failed to pull subtree ... Adding subtree first"
  git subtree add --prefix=raw/server $LOC documentation --squash -m "Update server subtree"
fi

cp -f $HOME/README.rst .
git add -f .
git commit -m "Latest server documentation file on successful travis build $TRAVIS_BUILD_NUMBER auto-pushed to dev.loklak.org"
git push -f origin master

if [ $? -eq 0 ]; then
  echo -e "Published Raw files to dev.loklak.org.\n"
  exit 0
else
  echo -e "Publishing failed. Maybe the access-token was invalid or had insufficient permissions.\n"
  exit 1
fi
