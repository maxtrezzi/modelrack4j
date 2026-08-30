#!/usr/bin/env bash
#
# ProviderSwap — two requests. The provider as configuration.
#
# Run './run-swap.sh --help' for what it does and what it costs.
# The work is in build/run-example.sh, shared with the other three.

exec "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/build/run-example.sh" swap "$@"
