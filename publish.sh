#!/bin/bash

echo "Building uberjar"
lein uberjar

echo "Updating tango-pub"
cd ../tango-pub
git pull
cd -

echo "Copying uberjar"
cp target/uberjar/tango-0.1.3-SNAPSHOT-standalone.jar ../tango-pub
cd ../tango-pub
git add tango-0.1.3-SNAPSHOT-standalone.jar

echo "Commiting uberjar"
git commit -m "latest build"

echo "Publishing uberjar"
git push
