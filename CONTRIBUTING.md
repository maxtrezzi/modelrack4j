# Contributing

Thanks for looking. This is a small, opinionated library with a narrow scope, so the most
useful thing you can do before writing code is **open an issue**.

## Open an issue first

Please do that for anything beyond an obvious typo — a bug report, a question, or a sketch of
a change you are considering. A short conversation costs you ten minutes; a large unsolicited
pull request that does not fit the scope costs us both far more, and is unpleasant to decline.

That is the only real rule here.

## Before proposing a feature

Two documents will answer most "would you take a PR for X?" questions faster than an issue
will:

- **[Out of scope](README.md#out-of-scope)** — things this library will deliberately never do.
  `AiServices`, tools, RAG, retry and fallback pools are all on that list, and the answer for
  them is settled rather than undecided.
- **[`docs/adr/`](docs/adr/README.md)** — every design decision, with the alternatives that
  were rejected and why. If a change would contradict one of these, that is not a blocker, but
  the discussion starts from the reasoning already recorded there rather than from scratch.

## Building

```bash
mvn clean install                        # full build and tests
mvn -pl modelrack4j-core -am test        # core only
```

**The default build must pass offline, with no API keys.** Tests that talk to a real provider
are `*IT` classes behind `-Pintegration`, and each one skips itself when its own key is
absent. A CI job runs the build with the credential environment scrubbed specifically to catch
a test that quietly grew a dependency on a key.

**Do not add the mutation testing plugin to another module, and do not move it to the parent
`pom.xml`.** The parent passes its plugins to every module, including the four provider
modules, whose `*IT` tests call paid APIs. Mutation testing runs the tests once for every
change it makes, so one run in the wrong module can cost real money
([ADR-0041](docs/adr/0041-mutation-testing-on-core-only.md)).

Java 17 is the floor; CI runs 17, 21 and 25.

## Pull requests

- One branch per change, never committed straight to `main`. This is enforced, not only
  asked: `main` requires a pull request, and force-pushing and deletion are blocked
  ([ADR-0040](docs/adr/0040-protect-main-with-required-checks-not-required-review.md)).
- The build stays green: `mvn clean verify` before you push. The five checks in
  `.github/workflows/build.yml` — JDK 17, 21 and 25, docs consistency, and the offline
  no-API-keys build — must pass and be current with the branch before a PR can merge. No
  approving review is required, so a green build is the whole gate.
- New behaviour comes with a test. A test that cannot fail is worse than no test — if it
  guards against a specific fault, break the code and confirm it catches it.
- If you changed logic in `modelrack4j-core`, run mutation testing before you open the pull
  request. This is the automatic form of the rule above. Nothing enforces it: no build step
  runs it and no CI check requires it.

  ```bash
  mvn -pl modelrack4j-core org.pitest:pitest-maven:mutationCoverage
  ```

  It changes the code in small ways and reports which changes no test noticed. Each one is a
  question about the tests, not a defect in the library: write a test, exclude the code, or
  decide the change means the same thing as the original. The report is written to
  `modelrack4j-core/target/pit-reports/` and takes a few minutes. It reads core only, so it
  has nothing to say about a change to a provider module, to the examples or to the
  documentation.
- If your change settles a design question, it needs an ADR. Copy
  [`docs/adr/0000-template.md`](docs/adr/0000-template.md), take the next free number after the
  ones already on `main`, and add a row to the index. Two open pull requests can pick the same
  number and both be right, so renumber yours if another one merges first — an ADR number is
  only settled once it is on `main`.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE), the same terms that cover the project.
