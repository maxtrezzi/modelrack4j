#!/usr/bin/env bash
#
# ThreeModelCouncil — three requests. Three models, one question you type.
#
# Run './run-council.sh --help' for what it does and what it costs.
# The work is in build/run-example.sh, shared with the other three.

exec "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/build/run-example.sh" council "$@"
