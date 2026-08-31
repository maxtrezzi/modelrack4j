#!/usr/bin/env bash
#
# DatabaseSource — free, and sends no request.
#
# Run './run-database.sh --help' for what it does and what it costs.
# The work is in build/run-example.sh, shared with the other four.

exec "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/build/run-example.sh" database "$@"
