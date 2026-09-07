#!/usr/bin/env bash
#
# CustomProperties — free, and sends no request.
#
# Run './run-properties.sh --help' for what it does and what it costs.
# The work is in build/run-example.sh, shared with the other five.

exec "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/build/run-example.sh" properties "$@"
