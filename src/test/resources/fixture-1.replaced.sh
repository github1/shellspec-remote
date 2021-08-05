echo 'define; Fsetup_ ZnVuY3Rpb24gRnNldHVwXygpIHsKaG9zdG5hbWUKfQo=' | nc localhost 1234
echo 'define; Fcleanup_ ZnVuY3Rpb24gRmNsZWFudXBfKCkgewpob3N0bmFtZQp9Cg==' | nc localhost 1234
echo 'define; FsendGraph_ ZnVuY3Rpb24gRnNlbmRHcmFwaF8oKSB7CmVjaG8gIm1hcmtldF9zZWdtZW50Igp9Cg==' | nc localhost 1234
f_invoke_remote(){
local l_FID="${1}"
shift
local l_UUID=$(uuidgen)
echo 'invoke; '${l_UUID}' '${l_FID}' '$(echo "$@" | base64) | nc localhost 1234
while [[ ! -f "/tmp/responsefile${l_UUID}" ]]; do
sleep .25
done
cat /tmp/responsefile${l_UUID}
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