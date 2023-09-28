# shellspec-remote

## Install

```shell
git clone [repo-url]
ln -s ${PWD}/shellspec-remote/bin/cli.sh /usr/local/bin/shellspec-remote
```

## Usage

```
shellspec-remote ./README.md
```

## Examples

```bash
./bin/cli.sh ./README.md
```

```bash
# @OnHost
setup() {
  bash -c "while true; do sleep 1; done" &
  background_process_PID=$!
}
# @OnHost
cleanup() {
  if [[ -n "${background_process_PID}" ]]; then
    echo "killing ${background_process_PID}"
    kill -9 ${background_process_PID}
  else
    echo "background_process_PID not found"
  fi
}
# @OnHost
remote_var_is_set() {
  [[ -n "${!1}" ]]
}
# @OnHost
remote_uname() {
  uname -a
}
# @OnHost
remote_exit_with() {
  exit "$1"
}
# @OnHost
remote_file() {
  cat ./sample.json
}
local_pipe_from_remote() {
  remote_file | jq .
}
local_uname() {
  uname -a
}
local_exit_with() {
  return "$1"
}
Context 'shellspec-remote'
  BeforeAll 'setup'
  AfterAll 'cleanup'
  Describe 'calling a function with the @OnHost annotation'
      It 'can run remote background processes'
        Assert remote_var_is_set background_process_PID
      End
      It 'should execute on the docker host'
        When call remote_uname
        The output should match pattern "*Darwin*"
        The output should not equal "$(local_uname)"
      End
      It 'can exit with a code'
        When call remote_exit_with 3
        The status should eq 3
      End
  End
  Describe 'calling a function without the @OnHost annotation'
      It 'should execute inside the container'
        When call local_uname
        The output should match pattern "*Linux*"
      End
      It 'can exit with a code'
        When call local_exit_with 3
        The status should eq 3
      End
  End
End
```

## License

[MIT](LICENSE.md)
