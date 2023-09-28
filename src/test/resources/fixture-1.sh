# @OnHost
setup() {
  hostname
}
# @OnHost
cleanup() {
  hostname
}
# @OnHost
hasExit() {
  exit 4
}
# @OnHost
sendGraph() { echo "market_segment"; }
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