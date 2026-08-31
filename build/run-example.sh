#!/usr/bin/env bash
#
# Shared implementation behind the five run-*.sh scripts in the repository root. Not meant to
# be called directly: run ./run-atomic.sh, ./run-database.sh, ./run-swap.sh, ./run-chat.sh
# or ./run-council.sh.
#
# The exec:java command these examples need is long, and three parts of it are easy to get
# wrong: the fully qualified main class, the path to the configuration file, and the fact
# that exec:java resolves modelrack4j-core from ~/.m2 rather than from the reactor, so the
# project has to be installed first. This supplies all three.
#
# There are no .bat counterparts. Shipping a Windows script nobody here can run would ship an
# untested file; on Windows, use the mvn command each script's --help prints.

set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
package="io.github.maxtrezzi.modelrack4j.examples"
default_config="modelrack4j-examples/src/main/resources/examples.conf"

case "${1-}" in
    atomic)
        script="run-atomic.sh";  main="AtomicSnapshot";    needs_keys=false; takes_config=false
        cost="free, and sends no request"
        shows="One save changes two models at once while four threads read both. Reading through
snapshot() never catches a mixed pair; reading through two get() calls sometimes does."
        ;;
    database)
        script="run-database.sh"; main="DatabaseSource";   needs_keys=false; takes_config=false
        cost="free, and sends no request"
        shows="Configuration held in memory instead of a file, standing in for a database row,
with the application calling reload() itself. Shows all four answers reload() can give."
        ;;
    swap)
        script="run-swap.sh";    main="ProviderSwap";      needs_keys=true;  takes_config=false
        cost="two requests"
        shows="The same call site answered by Anthropic, then by OpenAI, after a file edit."
        ;;
    chat)
        script="run-chat.sh";    main="ConsoleChat";       needs_keys=true;  takes_config=true
        cost="a conversation"
        shows="An interactive menu of every configured model. Edit the configuration while it
runs and the menu changes underneath you."
        ;;
    council)
        script="run-council.sh"; main="ThreeModelCouncil"; needs_keys=true;  takes_config=true
        cost="three requests"
        shows="Three models, one question, no provider branch anywhere in the code."
        ;;
    *)
        echo "run-example.sh is the shared implementation behind ./run-atomic.sh," >&2
        echo "./run-database.sh, ./run-swap.sh, ./run-chat.sh and ./run-council.sh. Run one of those." >&2
        exit 2
        ;;
esac
shift

usage() {
    echo "$script — $main ($cost)"
    echo
    echo "$shows"
    echo
    if [ "$takes_config" = true ]; then
        echo "Usage: ./$script [--build] [config-file ...]"
        echo
        echo "Defaults to $default_config."
        echo "Pass several files to see layering: applied lowest precedence first, last one wins."
    else
        echo "Usage: ./$script [--build]"
        echo
        echo "Writes its own configuration at run time, so it takes no file."
    fi
    echo
    if [ "$needs_keys" = true ]; then
        echo "Needs ANTHROPIC_API_KEY and OPENAI_API_KEY. A .env file in the repository root is"
        echo "loaded if present, so a key you left there is used without being asked for."
        echo "./run-atomic.sh and ./run-database.sh need no key and cost nothing."
        echo
    fi
    echo "  --build   run \`mvn install\` first even if the project is already installed. Do this"
    echo "            after changing library code: exec:java reads ~/.m2, so a stale install"
    echo "            runs old code."
    echo
    echo "On Windows, run this instead:"
    echo "  mvn install"
    if [ "$takes_config" = true ]; then
        echo "  mvn -q -pl modelrack4j-examples exec:java -Dexec.mainClass=$package.$main \\"
        echo "      -Dexec.args=$default_config"
    else
        echo "  mvn -q -pl modelrack4j-examples exec:java -Dexec.mainClass=$package.$main"
    fi
}

case "${1-}" in
    -h|--help|help) usage; exit 0 ;;
esac

build=false
if [ "${1-}" = "--build" ]; then
    build=true
    shift
fi

args=""
if [ "$takes_config" = true ]; then
    if [ "$#" -eq 0 ]; then
        set -- "$default_config"
    fi
    for config in "$@"; do
        if [ ! -f "$root/$config" ] && [ ! -f "$config" ]; then
            echo "No such configuration file: $config" >&2
            exit 1
        fi
    done
    args="$*"
elif [ "$#" -gt 0 ]; then
    echo "$main takes no configuration file: it writes its own at run time." >&2
    echo "Run './$script --help' for what it does accept." >&2
    exit 2
fi

if [ -f "$root/.env" ]; then
    echo "Loading $root/.env"
    set -a
    # shellcheck disable=SC1091
    . "$root/.env"
    set +a
fi

if [ "$needs_keys" = true ]; then
    missing=""
    for key in ANTHROPIC_API_KEY OPENAI_API_KEY; do
        if [ -z "${!key-}" ]; then
            missing="$missing $key"
        fi
    done
    if [ -n "$missing" ]; then
        echo "$main sends real requests and needs:$missing" >&2
        echo "Set them in the environment or in a .env file, or run './run-atomic.sh'" >&2
        echo "or './run-database.sh', which cost nothing and need no key." >&2
        exit 1
    fi
    echo "$main sends real requests to a paid API ($cost)."
fi

cd "$root"

if [ "$build" = true ] || [ ! -d "$HOME/.m2/repository/io/github/maxtrezzi/modelrack4j-core" ]; then
    echo "Installing the project — exec:java resolves modelrack4j-core from ~/.m2."
    mvn -q install
fi

echo "Running $main"
if [ -n "$args" ]; then
    exec mvn -q -pl modelrack4j-examples exec:java \
        -Dexec.mainClass="$package.$main" \
        -Dexec.args="$args"
else
    exec mvn -q -pl modelrack4j-examples exec:java \
        -Dexec.mainClass="$package.$main"
fi
