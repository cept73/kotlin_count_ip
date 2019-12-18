#!/bin/bash

# Настройки
BUILD_DIR=_build
BUILD_FILE=main.jar

# Подготавливаем файловую систему
if ! [ -d "$BUILD_DIR" ]; then mkdir "$BUILD_DIR"; fi
rm -f "$BUILD_DIR/$BUILD_FILE"

# Компилируем
kotlinc "main.kt" -include-runtime -d "$BUILD_DIR/$BUILD_FILE"

# Запускаем
./run.sh $1
