#!/usr/bin/env bash
#
# LocalDevelopment — free. A development layer on Ollama over a production layer on OpenAI.
#
# Run './run-local.sh --help' for what it does and what it costs.
# The work is in build/run-example.sh, shared with the other six.

exec "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/build/run-example.sh" local "$@"
