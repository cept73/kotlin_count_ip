#!/bin/bash

echo ""
echo ""
echo ""

rm -f "_build/main.jar"
kotlinc "main.kt" -include-runtime -d "_build/main.jar"

./run.sh $1
