#!/usr/bin/env bash
#
# Checks the bundle that `-Prelease` uploads to Maven Central, before it is uploaded.
#
# Two things a comment in a POM cannot check:
#
#   1. modelrack4j-examples is not in the bundle. Under -Prelease the `maven.deploy.skip`
#      property in that module is dead — the Central publishing plugin removes
#      maven-deploy-plugin from the build — and only `skipPublishing` keeps it out.
#   2. Every artifact carries a detached signature. Central rejects a bundle without them,
#      but it rejects it after the upload; this says so first.
#
# Build a bundle without uploading anything (both URLs must be blocked: a release goes to
# centralBaseUrl, a -SNAPSHOT goes straight to centralSnapshotsUrl instead):
#
#   mvn -Prelease -DcentralBaseUrl=http://127.0.0.1:1 \
#                 -DcentralSnapshotsUrl=http://127.0.0.1:1 clean deploy
#
# The upload then fails, which is the point. The bundle it wrote is still there:
#
#   build/check-release-bundle.sh
#
# Requires Maven 3.9.2 or newer, which the release profile's own enforcer rule states.

set -euo pipefail

BUNDLE="${1:-target/central-publishing/central-bundle.zip}"

if [[ ! -f "$BUNDLE" ]]; then
  echo "no bundle at $BUNDLE — run the dry run described at the top of this script" >&2
  exit 1
fi

entries="$(unzip -Z1 "$BUNDLE")"
problems=0

echo "bundle: $BUNDLE"
echo "modules:"
echo "$entries" | sed -n 's|^io/github/maxtrezzi/\([^/]*\)/.*|  \1|p' | sort -u

excluded="$(echo "$entries" | grep -c 'modelrack4j-examples' || true)"
if [[ "$excluded" -ne 0 ]]; then
  echo "FAIL: modelrack4j-examples appears $excluded time(s) in the bundle" >&2
  problems=$((problems + 1))
else
  echo "ok: no modelrack4j-examples entry"
fi

# Every .jar and .pom needs a sibling .asc. Checksums (.md5/.sha1/.sha256/.sha512) do not.
missing=0
while read -r artifact; do
  [[ -z "$artifact" ]] && continue
  if ! echo "$entries" | grep -qxF "$artifact.asc"; then
    echo "FAIL: no signature for $artifact" >&2
    missing=$((missing + 1))
  fi
done < <(echo "$entries" | grep -E '\.(jar|pom)$' || true)

if [[ "$missing" -ne 0 ]]; then
  echo "FAIL: $missing artifact(s) without a .asc — was the bundle built with -Dgpg.skip=true?" >&2
  problems=$((problems + 1))
else
  echo "ok: every .jar and .pom has a .asc"
fi

if [[ "$problems" -ne 0 ]]; then
  echo "$problems problem(s)" >&2
  exit 1
fi

echo "bundle looks publishable"
