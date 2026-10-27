#!/usr/bin/env bash
#
# Scans every pom.xml under a directory for dependencies with a known vulnerability, using
# osv-scanner and the OSV database (ADR-0064). Run by .github/workflows/vulnerabilities.yml.
#
#   build/osv-scan.sh [directory]       default: the current directory
#
# OSV_SCANNER_VERSION and OSV_SCANNER_SHA256 come from the workflow, which pins both. The
# binary is checked against that checksum before it runs.
#
# Fails when osv-scanner reports a vulnerability, fails on any other error, and also fails
# when the scan found no packages: an unresolved dependency graph produces an empty result,
# and an empty result must not read as a clean one.
#
# A finding that does not apply goes in osv-scanner.toml at the root, with a reason and an
# ignoreUntil date, never as a change to this script.
set -euo pipefail

dir="${1:-.}"
: "${OSV_SCANNER_VERSION:?set by the workflow}" "${OSV_SCANNER_SHA256:?set by the workflow}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
bin="$work/osv-scanner"

curl -sSfL -o "$bin" \
  "https://github.com/google/osv-scanner/releases/download/${OSV_SCANNER_VERSION}/osv-scanner_linux_amd64"
echo "${OSV_SCANNER_SHA256}  ${bin}" | sha256sum -c -
chmod +x "$bin"
"$bin" --version

status=0
"$bin" scan source --recursive "$dir" 2>&1 | tee "$work/scan.log" || status=$?

found="$(grep -oE 'found [0-9]+ packages?' "$work/scan.log" | grep -oE '[0-9]+' \
  | awk '{ total += $1 } END { print total + 0 }')"
echo "packages scanned: $found"
if [[ "$found" -eq 0 ]]; then
  echo "FAIL: osv-scanner reported no packages, so nothing was checked" >&2
  exit 1
fi

exit "$status"
