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
  BPID=$!
}
# @OnHost
cleanup() {
  if [[ -n "${BPID}" ]]; then
    echo "killing ${BPID}"
    kill -9 ${BPID}
  else
    echo "BPID not found"
  fi
}
# @OnHost
remote_uname() {
  uname -a
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
Context 'shellspec-remote'
  BeforeAll 'setup'
  AfterAll 'cleanup'
  Describe 'calling a function with the @OnHost annotation'
      It 'should execute on the docker host'
        When call remote_uname
        The output should match pattern "*Darwin*"
        The output should not equal "$(local_uname)"
      End
  End
  Describe 'calling a function without the @OnHost annotation'
      It 'should execute inside the container'
        When call local_uname
        The output should match pattern "*Linux*"
      End
  End
End
```

## License

[MIT](LICENSE.md)
