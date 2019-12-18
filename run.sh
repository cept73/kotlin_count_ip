#!/bin/bash

# Настройки
APP_FILE=_build/main.jar

# Проверяем существование файла, если есть - запускаем
if [ -f "$APP_FILE" ]; then java -jar $APP_FILE $1
else echo Сначала скомпилируйте программу с помощью build.sh
fi
