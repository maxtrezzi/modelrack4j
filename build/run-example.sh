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
        layered=false
        cost="free, and sends no request"
        shows="One save changes two models at once while four threads read both. Reading through
snapshot() never catches a mixed pair; reading through two get() calls sometimes does."
        ;;
    database)
        script="run-database.sh"; main="DatabaseSource";   needs_keys=false; takes_config=false
        layered=false
        cost="free, and sends no request"
        shows="Configuration held in memory instead of a file, standing in for a database row,
driven by the application itself. Shows all four answers reload() can give, then the same
rejected change offered through store(), which refuses it before the row is written."
        ;;
    swap)
        script="run-swap.sh";    main="ProviderSwap";      needs_keys=true;  takes_config=false
        layered=false
        cost="two requests"
        shows="The same call site answered by Anthropic, then by OpenAI, after a file edit."
        ;;
    chat)
        script="run-chat.sh";    main="ConsoleChat";       needs_keys=true;  takes_config=true
        layered=true
        cost="a conversation"
        shows="An interactive menu of every configured model. Edit the configuration while it
runs and the menu changes underneath you. Type /tools during a chat and the next questions go
through an AiService with a @Tool method, built on that turn's bundle."
        ;;
    council)
        script="run-council.sh"; main="ThreeModelCouncil"; needs_keys=true;  takes_config=true
        layered=false
        cost="three requests per question"
        shows="Three models, one question at a time, no provider branch anywhere in the code.
It asks you for a question, all three answer it, and it asks again until you type /exit."
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
    if [ "$takes_config" = true ] && [ "$layered" = true ]; then
        echo "Usage: ./$script [--build] [config-file ...]"
        echo
        echo "Defaults to $default_config."
        echo "Pass several files to see layering: applied lowest precedence first, last one wins."
    elif [ "$takes_config" = true ]; then
        echo "Usage: ./$script [--build] [config-file]"
        echo
        echo "Defaults to $default_config."
        echo "$main reads one file. ./run-chat.sh is the example that takes several"
        echo "and layers them."
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
    if [ "$layered" != true ] && [ "$#" -gt 1 ]; then
        echo "$main reads one configuration file, and $# were given." >&2
        echo "Run './run-chat.sh' for the example that layers several." >&2
        exit 2
    fi
    resolved=""
    for config in "$@"; do
        # Resolved to an absolute path here, because the exec below runs from the repository
        # root: a path relative to the directory the caller was standing in would pass this
        # check and then not be found. Repository-relative is tried first, so the paths the
        # --help prints keep working from anywhere.
        if [ -f "$root/$config" ]; then
            absolute="$root/$config"
        elif [ -f "$config" ]; then
            absolute="$(cd -- "$(dirname -- "$config")" && pwd)/$(basename -- "$config")"
        else
            echo "No such configuration file: $config" >&2
            exit 1
        fi
        # exec:java splits -Dexec.args on whitespace, so a path containing a space would
        # arrive at the example as two paths. Refuse it rather than fail confusingly.
        case "$absolute" in
            *[[:space:]]*)
                echo "Configuration path contains a space, which exec:java splits on:" >&2
                echo "  $absolute" >&2
                echo "Move the file to a path without spaces." >&2
                exit 1
                ;;
        esac
        resolved="${resolved:+$resolved }$absolute"
    done
    args="$resolved"
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

# JDK 24 and later print a warning for every sun.misc.Unsafe memory-access call, and Maven's
# own bundled Guava makes one. exec:java runs the example inside the Maven JVM, so that
# warning lands in the middle of the example's output, blaming a jar the project does not
# depend on. This flag permits the call without the warning.
#
# It is probed rather than assumed: the flag does not exist before JDK 23, an unrecognised
# launcher option is fatal rather than ignored, and this project's floor is Java 17.
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if "$java_bin" --sun-misc-unsafe-memory-access=allow -version >/dev/null 2>&1; then
    export MAVEN_OPTS="${MAVEN_OPTS:+$MAVEN_OPTS }--sun-misc-unsafe-memory-access=allow"
fi

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
