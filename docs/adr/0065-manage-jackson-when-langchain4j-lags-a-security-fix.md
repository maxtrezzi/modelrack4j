# ADR-0065: Manage Jackson in the parent and the BOM while LangChain4j brings a vulnerable one

- **Status:** Accepted
- **Date:** 2026-10-27
- **Supersedes:** —
- **Amends:** —

## Context

`langchain4j-core` `1.22.0`, the newest release on 2026-10-27, declares `jackson-core` and
`jackson-databind` `2.22.1` in its own POM; the LangChain4j BOM does not manage Jackson. The
OSV database lists seven advisories against `2.22.1`, five rated high, published between
2026-09-28 and 2026-10-01, all fixed in `2.22.3`: GHSA-7hhh-6rmp-j9qf, GHSA-p6pp-m3f8-5c89,
GHSA-cxp5-3px4-pw24, GHSA-gx83-3vf8-gh7j, GHSA-q4xh-88c3-wmh7, GHSA-wjgm-6hv5-3cvf and
GHSA-wv8q-qhhj-9h54. Most are a denial of service while parsing. `2.22.3` and `2.22.4` are on
Central, and OSV lists nothing against either.

Both artifacts reach every user of this library at compile scope, through `langchain4j-core`.
The released `0.3.0`, built on LangChain4j `1.20.0`, carries `jackson-databind` `2.22.1` too.

ADR-0005, as amended by ADR-0020 and ADR-0028, fixes core's compile dependencies at four.

## Forces

- **A direct dependency in core** would reach every user: Maven picks the nearest version, and
  core is one step closer than `langchain4j-core`. It breaks the four-dependency rule, and its
  version becomes one more thing to move by hand on every LangChain4j bump, long after
  LangChain4j has fixed its own.
- **Managing the version in the parent POM** fixes this project's build and tests, and changes
  nothing for an application, because Maven applies only the application's own
  `dependencyManagement` to the graph.
- **Managing it in `modelrack4j-bom`** reaches every application that imports the BOM, one of
  the two ways the README shows to add the library. One that does not import it keeps `2.22.1`.
  Measured with a throwaway project against this build: `2.22.4` with the BOM imported,
  `2.22.1` without it.
- **Only documenting** leaves this project's own build on a vulnerable version, and the check
  of ADR-0064 red.

## Decision

1. **The parent POM names `jackson.version` = `2.22.4` and manages `jackson-core` and
   `jackson-databind` with it.** `jackson-annotations` stays where LangChain4j puts it, `2.22`,
   which has no advisory.
2. **`modelrack4j-bom` manages the same two artifacts, from the same property.** One value, two
   places that use it.
3. **Core declares no Jackson dependency.** The four-dependency rule stands.
4. **The CHANGELOG tells an application that does not import the BOM how to override Jackson
   itself**, and says that `0.3.0` is affected too.
5. **Remove the entries when a LangChain4j release brings a fixed Jackson itself.** Re-check
   them on every LangChain4j bump, as the `jspecify` pin was.

## Consequences

- This project builds and tests on a Jackson with no known advisory: queried on OSV after the
  change, none of the 41 third-party artifacts the modules resolve has one.
- An application importing `modelrack4j-bom` gets the fixed version with nothing to do. One that
  does not, does not — the CHANGELOG is the only thing that tells it.
- The BOM now manages two artifacts that are not this project's. The BOM already exported the
  parent's whole `dependencyManagement` through inheritance — 225 entries, LangChain4j's, JUnit's
  and AssertJ's among them — which P58 raises as a separate question; these two entries are
  explicit so that they stay if that changes.
- A Jackson version above LangChain4j's own is one LangChain4j was not tested with. A patch
  release within `2.22` is the smallest step that fixes the advisories, and this project's 350
  offline tests pass on it.
