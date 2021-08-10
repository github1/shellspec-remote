#!/usr/bin/env bash

# @OnHost
function func_stream_output() {
  local l_COUNT=0
  while [[ "${l_COUNT}" != 3 ]]; do
    echo "{\"hostname\":\"$(hostname)\"}"
    sleep .25
    l_COUNT=$((l_COUNT+1))
  done
}

# @OnHost
function func_stream_output_2() {
  SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
  bash ${SCRIPT_DIR}/src/test/resources/fixture-main-include.sh &
  wait $!
}

# @OnHost
function func_fails() {
  return 1
}

# @OnHost
function background_process() {
  bash -c "while true; do sleep 1; done" &
  BPID=$!
  echo "started ${BPID}"
  sleep .25
  kill -9 ${BPID}
}

function local_pipe_from_remote() {
  func_stream_output | jq .
}

eval ${FUNC}