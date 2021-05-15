# shellspec-remote

[description]

[![build status](https://img.shields.io/travis/github1/common/master.svg?style=flat-square)](https://travis-ci.org/github1/common)
[![npm version](https://img.shields.io/npm/v/packages/common.svg?style=flat-square)](https://www.npmjs.com/package/packages/common)
[![npm downloads](https://img.shields.io/npm/dm/packages/common.svg?style=flat-square)](https://www.npmjs.com/package/packages/common)

## Install

```shell
git clone [repo-url]
ln -s ${PWD}/shellspec-remote/bin/cli.sh /usr/local/bin/shellspec-remote
```

## Usage

```
shellspec-remote some_spec.sh
```

## Examples

```bash
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
  Describe 'calling a function with the @OnHost annotation'
      It 'should execute on the docker host'
        When call remote_uname
        The output should match pattern "*Darwin*"
        The output should not equal "$(local_uname)"
      End
      It 'can pipe output from remote functions to a local functions'
        When call local_pipe_from_remote
        The output should match pattern "*foo*"
      End
  End
  Describe 'calling a function without the @OnHost annotation'
      It 'should execute inside the container'
        When call local_uname
        The output should match pattern "*GNU/Linux*"
      End  
  End
End
```

## License
[MIT](LICENSE.md)
