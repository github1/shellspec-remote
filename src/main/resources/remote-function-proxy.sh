f_invoke_remote() {
  local l_FID="${1}"
  shift
  local l_SESSION_TEMP_DIR="<SESSION_TEMP_DIR>"
  local l_UUID=$(<GEN_UUID>)
  touch "${l_SESSION_TEMP_DIR}/responsefile${l_UUID}"
  echo 'invoke; '${l_UUID}' '${l_FID}' '$(echo "$@" | base64) | <NC_SEND> &
  tail -F "${l_SESSION_TEMP_DIR}/responsefile${l_UUID}" | while read line; do
    if echo $line | egrep -q '^exit; [0-9]+$'; then
      pkill -fx "tail -F ${l_SESSION_TEMP_DIR}/responsefile${l_UUID}"
      break
    else
      echo $line
    fi
  done
  local l_EXIT_STATUS=$(tail -n 1 "${l_SESSION_TEMP_DIR}/responsefile${l_UUID}" | awk '{print $2}')
  rm "${l_SESSION_TEMP_DIR}/responsefile${l_UUID}"
  if [[ "${l_EXIT_STATUS}" != "0" ]]; then
    exit ${l_EXIT_STATUS}
  fi
}
