#!/usr/bin/env bash
#
# Looks up every dependency this build ships for a known vulnerability, in the OSV database
# (ADR-0064). Run by .github/workflows/vulnerabilities.yml.
#
#   build/osv-scan.sh [directory]       the Maven project to scan; default: the current one
#
# Maven resolves the graph and writes it as a CycloneDX SBOM; osv-scanner reads that file. The
# order matters. Given the pom.xml files directly, osv-scanner resolves the graph itself, from
# Maven Central, and cannot find this project's own snapshot modules there: for every module
# that depends on modelrack4j-core it then gives up on the transitive dependencies and still
# reports no vulnerabilities. Maven resolves them from the reactor, applies the parent POM's
# dependencyManagement, and so lists exactly the versions the build uses. Test scope is left
# out, because it does not reach anyone who depends on the library.
#
# OSV_SCANNER_VERSION, OSV_SCANNER_SHA256 and CYCLONEDX_VERSION come from the workflow, which
# pins all three. The osv-scanner binary is checked against that checksum before it runs.
#
# Fails when osv-scanner reports a vulnerability or any error, when its output mentions a
# failed resolution or extraction, and when it scanned fewer packages than the SBOM lists: a
# result that checked less than the build ships must not read as a clean one.
#
# A finding that does not apply goes in osv-scanner.toml at the root, with a reason and an
# ignoreUntil date, never as a change to this script.
set -euo pipefail

dir="${1:-.}"
: "${OSV_SCANNER_VERSION:?set by the workflow}" "${OSV_SCANNER_SHA256:?set by the workflow}"
: "${CYCLONEDX_VERSION:?set by the workflow}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
bin="$work/osv-scanner"
sbom="$dir/target/bom.json"

mvn -B --no-transfer-progress -q -f "$dir/pom.xml" \
  "org.cyclonedx:cyclonedx-maven-plugin:${CYCLONEDX_VERSION}:makeAggregateBom" \
  -DoutputFormat=json -DoutputName=bom -DincludeTestScope=false
expected="$(python3 -I -c '
import json, sys
components = json.load(open(sys.argv[1])).get("components", [])
print(sum(1 for c in components if c.get("group") != "io.github.maxtrezzi"))
' "$sbom")"
echo "third-party components in the SBOM: $expected"
if [[ "$expected" -eq 0 ]]; then
  echo "FAIL: the SBOM lists no third-party component, so nothing would be checked" >&2
  exit 1
fi

curl -sSfL -o "$bin" \
  "https://github.com/google/osv-scanner/releases/download/${OSV_SCANNER_VERSION}/osv-scanner_linux_amd64"
echo "${OSV_SCANNER_SHA256}  ${bin}" | sha256sum -c -
chmod +x "$bin"
"$bin" --version

config=()
if [[ -f "$dir/osv-scanner.toml" ]]; then
  config=(--config "$dir/osv-scanner.toml")
fi

status=0
"$bin" scan source "${config[@]}" --lockfile "$sbom" 2>&1 | tee "$work/scan.log" || status=$?

if grep -qiE 'failed resolution|error during extraction' "$work/scan.log"; then
  echo "FAIL: osv-scanner could not read every dependency; see the lines above" >&2
  exit 1
fi
found="$(grep -oE 'found [0-9]+ packages?' "$work/scan.log" | grep -oE '[0-9]+' \
  | awk '{ total += $1 } END { print total + 0 }')"
echo "packages scanned: $found"
if [[ "$found" -lt "$expected" ]]; then
  echo "FAIL: osv-scanner scanned $found packages, fewer than the $expected in the SBOM" >&2
  exit 1
fi

exit "$status"
