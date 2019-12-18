#!/bin/bash

COMPILED=_build/main.jar

if [ -f "$COMPILED" ]; then
    java -jar $COMPILED $1
else
    echo Сначала скомпилируйте программу с помощью build.sh
fi
