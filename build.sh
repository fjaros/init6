#!/bin/bash

BUILD_INFO_FILE="src/main/scala/com/vilenet/BuildInfo.scala"
BUILD_NUMBER=$(git rev-list --count HEAD)
BUILD_HASH=$(git rev-list --max-count 1 HEAD)
OS=$(uname)

if [ "$OS" = "Linux" ]; then
    sed -e "s/<buildNumber>Unknown<\/buildNumber>/<buildNumber>$BUILD_NUMBER<\/buildNumber>/g" -i'' pom.xml
    sed -e "s/BUILD_NUMBER = \"Unknown\"/BUILD_NUMBER = \"$BUILD_NUMBER\"/g" -i'' $BUILD_INFO_FILE
    sed -e "s/BUILD_HASH = \"Unknown\"/BUILD_HASH = \"$BUILD_HASH\"/g" -i'' $BUILD_INFO_FILE
elif [ "$OS" = "Darwin" ]; then
    sed -e "s/<buildNumber>Unknown<\/buildNumber>/<buildNumber>$BUILD_NUMBER<\/buildNumber>/g" -i "" pom.xml
    sed -e "s/BUILD_NUMBER = \"Unknown\"/BUILD_NUMBER = \"$BUILD_NUMBER\"/g" -i "" $BUILD_INFO_FILE
    sed -e "s/BUILD_HASH = \"Unknown\"/BUILD_HASH = \"$BUILD_HASH\"/g" -i "" $BUILD_INFO_FILE
else
    echo "Unrecognized OS"
    exit
fi
mvn clean package
if [ "$OS" = "Linux" ]; then
    sed -e "s/<buildNumber>$BUILD_NUMBER<\/buildNumber>/<buildNumber>Unknown<\/buildNumber>/g" -i'' pom.xml
elif [ "$OS" = "Darwin" ]; then
    sed -e "s/<buildNumber>$BUILD_NUMBER<\/buildNumber>/<buildNumber>Unknown<\/buildNumber>/g" -i "" pom.xml
fi
git checkout -- $BUILD_INFO_FILE
