#!/usr/bin/env bash
#
# ThreeModelCouncil — three requests per question. Three models, the questions you type.
#
# Run './run-council.sh --help' for what it does and what it costs.
# The work is in build/run-example.sh, shared with the other five.

exec "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/build/run-example.sh" council "$@"
