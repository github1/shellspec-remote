#!/usr/bin/env bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
SESSION_TMP_DIR=${SESSION_TMP_DIR:-/tmp}

function log() {
  >&2 echo $@
}

cat | while read message; do
  args=${message#*;}
  command=${message%;"$args"}
  if [[ "${command}" == "define" ]]; then
    FUNC_ID=$(echo "${args}" | awk '{print $1}')
    FUNC_DEF=$(echo "${args}" | awk '{print $2}' | base64 --decode)
    eval "${FUNC_DEF}"
  elif [[ "${command}" == "invoke" ]]; then
    CALL_ID=$(echo "${args}" | awk '{print $1}')
    FUNC_ID=$(echo "${args}" | awk '{print $2}')
    FUNC_ARGS=$(echo "${args}" | awk '{$1=$2=""; print $0}' | base64 --decode)
    ${FUNC_ID} ${FUNC_ARGS} > ${SESSION_TMP_DIR}/responsefile${CALL_ID}
    echo "exit; $?" >> ${SESSION_TMP_DIR}/responsefile${CALL_ID}
  fi
done