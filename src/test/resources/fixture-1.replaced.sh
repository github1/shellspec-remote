echo 'define; Fsetup_ ZnVuY3Rpb24gRnNldHVwXygpIHsKaG9zdG5hbWUKfQo=' | nc localhost 1234
echo 'define; Fcleanup_ ZnVuY3Rpb24gRmNsZWFudXBfKCkgewpob3N0bmFtZQp9Cg==' | nc localhost 1234
echo 'define; FsendGraph_ ZnVuY3Rpb24gRnNlbmRHcmFwaF8oKSB7CmVjaG8gIm1hcmtldF9zZWdtZW50Igp9Cg==' | nc localhost 1234
f_invoke_remote() {
  local l_FID="${1}"
  shift
  local l_SESSION_TEMP_DIR="/tmp"
  local l_UUID=$(uuidgen)
  touch "${l_SESSION_TEMP_DIR}/responsefile${l_UUID}"
  echo 'invoke; '${l_UUID}' '${l_FID}' '$(echo "$@" | base64) | nc localhost 1234 &
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
# @OnHost
setup(){
f_invoke_remote Fsetup_ $@
}
# @OnHost
cleanup(){
f_invoke_remote Fcleanup_ $@
}
# @OnHost
sendGraph(){
f_invoke_remote FsendGraph_ $@
}
Context 'graph.sh'
  BeforeAll 'setup'
  AfterAll 'cleanup'
  Describe 'projects'
    It 'can get projects'
      When call sendGraph projects
      The output should match pattern "*market_segment*"
    End
  End
End