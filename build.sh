#!/bin/bash

BUILD_INFO_FILE="src/main/scala/com/vilenet/BuildInfo.scala"
BUILD_NUMBER=$(git rev-list --count HEAD)
BUILD_HASH=$(git rev-list --max-count 1 HEAD)

sed -e "s/<buildNumber>Unknown<\/buildNumber>/<buildNumber>$BUILD_NUMBER<\/buildNumber>/g" -i "" pom.xml
sed -e "s/BUILD_NUMBER = \"Unknown\"/BUILD_NUMBER = \"$BUILD_NUMBER\"/g" -i "" $BUILD_INFO_FILE
sed -e "s/BUILD_HASH = \"Unknown\"/BUILD_HASH = \"$BUILD_HASH\"/g" -i "" $BUILD_INFO_FILE
mvn clean package
git checkout -- pom.xml $BUILD_INFO_FILE
