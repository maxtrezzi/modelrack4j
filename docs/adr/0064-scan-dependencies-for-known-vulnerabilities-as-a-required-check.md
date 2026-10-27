# ADR-0064: Scan the dependencies for known vulnerabilities, as a required check and weekly

- **Status:** Accepted
- **Date:** 2026-10-27
- **Supersedes:** —
- **Amends:** ADR-0040 — the set of required checks grows from five to six

## Context

Nothing in the project looked for known vulnerabilities in the dependencies. On 2026-10-27,
during the pre-release check of 0.4.0 (P58), the repository had Dependabot alerts and secret
scanning disabled, no scanner in CI, and no ADR on the subject.

A one-off query of the OSV database, over the 41 third-party artifacts Maven resolves for the
modules, found seven advisories in two of them: `jackson-core` and `jackson-databind` 2.22.1,
five of them rated high, all fixed in 2.22.3. Both reach every user at compile scope through
`langchain4j-core`, and the released `0.3.0` carries the same version. ADR-0065 records what
was done about them. This ADR is about finding the next one.

The owner chose, among the options put to them: a job in CI rather than a step of the release
checklist or GitHub's own alerts, osv-scanner as the tool, and a required check with a weekly
run.

## Forces

**Which tool.**

- *osv-scanner* (Google, the OSV database). Reads `pom.xml` and resolves transitive
  dependencies itself, through deps.dev and Maven Central. Needs no secret. A finding that does
  not apply is recorded in `osv-scanner.toml` with a reason and an expiry date. Chosen.
- *GitHub's dependency-review action*, fed by a Maven dependency-graph submission. Fails only
  on what a pull request *introduces*, so a new advisory on an old dependency never turns an
  unrelated pull request red. It needs the dependency graph switched on and a job with write
  permission to submit it, and it never looks at what is already merged.
- *OWASP dependency-check*. The best known for Java, but slow without an NVD API key, and
  Sonatype's OSS Index now requires a token: two secrets in the repository for a job anybody's
  fork runs.
- *Trivy* (Aqua Security), which the owner asked about. Reading `pom.xml`, it shares
  osv-scanner's weakness: it rebuilds the graph itself, and its documentation says a parent POM
  it cannot reach leaves a version empty and the dependencies under it unscanned. Its real
  advantage is that it can scan jars instead, so a job could let Maven resolve the graph with
  `dependency:copy-dependencies` and scan exactly what the build uses. That costs a Java setup
  and a dependency download in the job, and a database of its own rather than OSV, where the
  Jackson advisories were found. The owner kept osv-scanner.

**Required or not.** ADR-0043 kept mutation testing out of CI partly because a check that turns
red for reasons outside the change under review is either ignored or blocks unrelated work.
This check does exactly that — an advisory published on Monday turns Tuesday's documentation
pull request red. The difference from mutation testing is that the red is *true*: a dependency
that ships to users has a known vulnerability, and the next release would carry it. It is
resolved the same way every time — move the dependency, or record why it does not apply, with
a date by which to look again. An informational check costs nothing to ignore. No check at all
is how `0.3.0` was published on 2026-10-04 with `2.22.1`, three days after the last of its
seven advisories.

**When it runs.** Only on pull requests, an advisory published between two of them stays
invisible until the next one, however long that takes. A weekly run also scans `main`, which
is what Maven Central holds (ADR-0061), so a red run there means a released version is
affected.

**Who resolves the graph.** Given the `pom.xml` files, osv-scanner resolves the graph itself,
from Maven Central. Run that way on this project on 2026-10-27, by the owner, it resolved core
and failed for the other seven modules — each depends on `modelrack4j-core:0.4.0-SNAPSHOT`,
which Central does not have — and still reported no vulnerabilities, from direct dependencies
only. A scanner that silently checks less than the build ships is worse than none. So Maven
resolves the graph, from the reactor and with the parent POM's `dependencyManagement`, and
writes it as a CycloneDX SBOM with `cyclonedx-maven-plugin`, invoked by its coordinates; the
SBOM is what osv-scanner reads. Generated from a clean tree with no snapshot installed, it
lists 28 third-party components, Jackson at the managed `2.22.4` among them. The cost is a Java
setup and a Maven run in the job.

**Trusting the scanner's silence anyway.** The script still fails on any non-zero exit code,
when the output mentions a failed resolution or extraction, and when osv-scanner reports fewer
packages than the SBOM lists.

## Decision

1. **`.github/workflows/vulnerabilities.yml` runs osv-scanner** on every push and pull request
   to `dev` and `main`, as the job `known vulnerabilities`, and weekly on Monday at 06:17 UTC,
   and on demand, over both `dev` and `main`.
2. **The work is in `build/osv-scan.sh`**, not in the workflow: Maven writes the SBOM, with
   test scope left out because it does not reach users; the script downloads osv-scanner at the
   version the workflow pins, checks it against the published SHA256, runs
   `scan source --lockfile` on the SBOM, and fails on any finding, on any error, on a failed
   resolution, and when fewer packages were scanned than the SBOM lists.
3. **`known vulnerabilities` is a required check on `dev` and `main`**, beside the five
   ADR-0040 lists.
4. **A finding is resolved by moving the dependency.** When that is not possible yet, it goes
   in `osv-scanner.toml` at the root with a reason and an `ignoreUntil` date, never as a change
   to the script or by removing the check.
5. **The version and the checksum move together**, in the workflow, and only after the new
   release has been read. The plugin version is pinned there too.

## Consequences

- A pull request can turn red without any change in it, when an advisory is published against
  a dependency. That is the point of the check, and the cost accepted with it.
- A vulnerable dependency can no longer reach a release unnoticed: the release pull request
  from `dev` to `main` runs the same job.
- The weekly run on `main` stays red from the day an advisory appears until the next release
  replaces what `main` holds. It is a report on the released version, not a request to change
  `main`, which only a release changes (ADR-0061).
- The check sees **this project's** resolution of the graph, including what the parent POM
  manages. An application that does not import `modelrack4j-bom` resolves the graph itself and
  can still get a version the check does not see; ADR-0065 is the example.
- Do not make the job informational to get a pull request merged. Record the finding, with a
  date, or fix it.
- Do not replace the pinned download with an unpinned one or a floating action tag: the binary
  decides whether code may merge.
- Do not go back to scanning the `pom.xml` files to save the Maven run. It is what produced the
  silent, partial result above.
