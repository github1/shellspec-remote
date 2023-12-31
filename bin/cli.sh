#!/usr/bin/env bash

DIR="$( cd "$(pwd)" &> /dev/null && pwd )"
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

HOST_BRIDGE_PORT=9997
HOST_BRIDGE_CMD="nc -kl ${HOST_BRIDGE_PORT}"
SESSION_TMP_DIR="/tmp/bridge-tmp"

if [[ "${DEBUG}" == "true" ]]; then
  EXTRA_DOCKER_ARGS='-p 5005:5005 -e JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"'
fi

trap cleanup exit

function cleanup() {
  rm -rf "${SESSION_TMP_DIR}" || true
  pkill -fx "${HOST_BRIDGE_CMD}" || true
  if [[ -n "${bridgePID}" ]]; then
    kill -15 "${bridgePID}"
  fi
}

BRIDGE_SCRIPT_PATH=$(dirname ${SCRIPT_DIR})/bridge.sh

export SESSION_TMP_DIR

# start host bridge
${HOST_BRIDGE_CMD} | ${BRIDGE_SCRIPT_PATH} &
bridgePID=$!

mkdir -p "${SESSION_TMP_DIR}"

SCRIPT_FILE=${1}
shift
SCRIPT_ARGS=$@

if [[ -f "${SCRIPT_FILE}" ]]; then

  SCRIPT_FILE_DIR="$( cd "$( dirname "${SCRIPT_FILE}" )" &> /dev/null && pwd )"
  SCRIPT_FILE="${SCRIPT_FILE_DIR}/$(basename "${SCRIPT_FILE}")"

  docker run --rm ${EXTRA_DOCKER_ARGS} \
    -e 'LOG_LEVEL' \
    -e 'SESSION_TMP_DIR' \
    -v "${SESSION_TMP_DIR}:${SESSION_TMP_DIR}" \
    -v "${SCRIPT_FILE}:${SCRIPT_FILE}:ro" \
    shellspec-remote -r "host.docker.internal:${HOST_BRIDGE_PORT}" ${SCRIPT_ARGS} ${SCRIPT_FILE}
fi