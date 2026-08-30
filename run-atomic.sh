#!/usr/bin/env bash
#
# AtomicSnapshot — free, and sends no request. Start here.
#
# Run './run-atomic.sh --help' for what it does and what it costs.
# The work is in build/run-example.sh, shared with the other three.

exec "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/build/run-example.sh" atomic "$@"
