#!/usr/bin/env bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
ROOT_DIR=$(dirname $(dirname $(dirname ${SCRIPT_DIR})))
DIR_NAME=$(basename ${SCRIPT_DIR})

cd ${ROOT_DIR}
./gradlew customFatJar
if [[ "$?" != "0" ]]; then
  exit 1
fi

cd ${SCRIPT_DIR}

docker build -t ${DIR_NAME}:latest -f ./docker/Dockerfile .