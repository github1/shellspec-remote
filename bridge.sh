#!/usr/bin/env bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
SESSION_TMP_DIR=${SESSION_TMP_DIR:-/tmp}

function log() {
  if [[ "${LOG_LEVEL}" == 'debug' ]]; then
    >&2 echo $@
  fi
}

cat | while read message; do
  args=${message#*;}
  command=${message%;"$args"}
  if [[ "${command}" == "define" ]]; then
    FUNC_ID=$(echo "${args}" | awk '{print $1}')
    FUNC_DEF=$(echo "${args}" | awk '{print $2}' | base64 --decode)
    log "define FUNC_ID=${FUNC_ID} FUNC_DEF=${FUNC_DEF}"
    eval "${FUNC_DEF}"
  elif [[ "${command}" == "invoke" ]]; then
    CALL_ID=$(echo "${args}" | awk '{print $1}')
    FUNC_ID=$(echo "${args}" | awk '{print $2}')
    FUNC_ARGS=$(echo "${args}" | awk '{$1=$2=""; print $0}' | base64 --decode)
    log "invoke CALL_ID=${CALL_ID} FUNC_ID=${FUNC_ID} FUNC_ARGS=${FUNC_ARGS}"
    ${FUNC_ID} ${FUNC_ARGS} > ${SESSION_TMP_DIR}/responsefile${CALL_ID}
    EXIT_STATUS=$?
    log "EXIT_STATUS=${EXIT_STATUS}"
    EXIT_STR="exit; ${EXIT_STATUS}";
    LAST_LINE=$(tail -c 1 ${SESSION_TMP_DIR}/responsefile${CALL_ID})
    if [[ "${LAST_LINE}" != "" ]]; then
      EXIT_STR="\n${EXIT_STR}"
    fi
    echo -e "${EXIT_STR}" >> ${SESSION_TMP_DIR}/responsefile${CALL_ID}
    if [[ "${LOG_LEVEL}" == 'debug' ]]; then
      log "responsefile=$(cat ${SESSION_TMP_DIR}/responsefile${CALL_ID})"
    fi
  fi
done