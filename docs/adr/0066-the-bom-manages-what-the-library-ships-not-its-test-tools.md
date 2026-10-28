# ADR-0066: The BOM manages what the library ships, not the tools it is tested with

- **Status:** Accepted
- **Date:** 2026-10-27
- **Supersedes:** —
- **Amends:** —

## Context

`modelrack4j-bom` inherits from `modelrack4j-parent`. An application that imports a BOM receives
the BOM's *effective* `dependencyManagement`, which includes everything the parent manages. On
2026-10-27 that was 225 entries: the two LangChain4j BOMs, Typesafe Config, SLF4J, Jackson, the
modelrack4j modules — and the JUnit BOM, AssertJ and Awaitility, which this project only tests
with.

Measured with the published `0.3.0` BOM: an application that imports it and declares
`junit-jupiter` and `assertj-core` without a version gets `6.1.3` and `3.27.7`. It asked for
neither. A version the application writes itself still wins, so nothing breaks; the
application's test setup is decided by a library it uses at runtime, which it has no reason to
expect.

Found in P58 and put to the owner in P59.

## Forces

- **Most of what the BOM exports is useful.** LangChain4j's artifacts must agree with each other,
  and an application that also uses LangChain4j directly — an `AiServices`, a tool — is best off
  on the version modelrack4j was built against. The same holds, less strongly, for Typesafe
  Config, SLF4J, and the Jackson version ADR-0065 moved to.
- **A BOM with no parent** is the usual shape, and exports exactly what it lists. But the parent
  carries what Maven Central requires of a published POM — name, description, URL, licence,
  developers, SCM — and the `release` profile that signs and uploads it (ADR-0045). A standalone
  BOM duplicates all of that, and `versions:set` then skips it unless told otherwise, which is
  one more step in a release that has already shown it drops steps.
- **Removing the test libraries from the parent's `dependencyManagement`** fixes the part that is
  wrong, and keeps the part that is useful, with no change to the release.

## Decision

1. **The parent's `dependencyManagement` holds no test library.** JUnit and AssertJ are declared
   with their versions in the parent's `<dependencies>`, which every module inherits and an
   imported BOM does not pass on; Awaitility with its version in `modelrack4j-core`, the only
   module that uses it. The versions stay properties of the parent.
2. **`modelrack4j-bom` keeps its parent.** It still manages the modelrack4j modules, Jackson, and,
   through the parent, the LangChain4j BOMs, Typesafe Config and SLF4J.
3. **What may be managed in the parent is what the library ships**: a dependency that reaches an
   application at compile or runtime scope. A tool the build or the tests use goes elsewhere.

## Consequences

- An application importing the BOM no longer has its JUnit or AssertJ version chosen for it.
  One that declared them without a version, relying on the BOM, now gets Maven's "version is
  missing" error and has to name a version; the CHANGELOG says so. Measured: 209 managed entries
  after the change, none of them JUnit, AssertJ, Awaitility, opentest4j or apiguardian.
- Adding a test library, or a plugin's runtime dependency, to the parent's
  `dependencyManagement` exports it to every application importing the BOM. Do not move them
  back there for tidiness; the note in the parent POM says why.
- The BOM still exports LangChain4j's whole BOM, which is a choice and not an accident: whether
  an application should take its LangChain4j version from modelrack4j is answered yes here.
