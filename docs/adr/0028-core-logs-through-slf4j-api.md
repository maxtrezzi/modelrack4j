# ADR-0028: The watcher logs, and core takes slf4j-api to do it

- **Status:** Accepted
- **Date:** 2026-08-23
- **Supersedes:** —
- **Amends:** [ADR-0020](0020-core-depends-on-langchain4j-aggregate.md) — adds a fourth
  compile dependency to the set that ADR describes as closed

## Context

Everything core did before M3 happened on the caller's thread and reported failure by
throwing. The watcher does not: it runs on its own daemon thread, and several of its
outcomes have no caller to throw at. A directory that cannot yet be re-registered, a listener
that throws, a watch service that does not close cleanly — each is worth knowing about and
none of them can propagate anywhere.

[ADR-0005](0005-provider-factory-spi-via-serviceloader.md) and ADR-0020 fixed core's
dependencies deliberately and narrowly: `langchain4j-core`, the `langchain4j` aggregate for
`ChatMemoryProvider` alone, and `com.typesafe:config`. Adding to that list is exactly the
kind of drift those ADRs exist to prevent, so it needs a decision rather than a commit.

## Forces

- **Silence is the worst option.** A watcher that stops re-registering, or a listener that
  throws on every reload, produces an application that looks healthy and has quietly stopped
  reloading. That is the failure shape this library is supposed to eliminate, not create.
- **`System.out` is not an option** in a library: not levelled, not filterable, not routed
  anywhere the application's own logs go.
- **A callback-based error channel was considered** — hand the application a
  `Consumer<Exception>` for watcher-internal problems. It puts a second, differently-shaped
  error path next to `onReloadFailure` for events that are almost always transient, and it
  makes the common case (log it) the application's work.
- **The dependency is not really new.** `slf4j-api` is already on core's compile classpath
  transitively through `langchain4j-core`, and every LangChain4j application already has a
  binding or has decided not to. The cost is a declaration, not a jar.
- **API only, never a binding.** A library that ships a binding takes the application's
  logging configuration away from it.

## Decision

Core declares `org.slf4j:slf4j-api` and logs through it. No binding, at any scope other than
test.

The version is pinned in the parent's `dependencyManagement`, because neither LangChain4j BOM
manages it, and it must be kept equal to the version `langchain4j-core` brings in — otherwise
the enforcer's dependency convergence rule fails, which is the intended alarm.

What is logged is deliberately small: transient watcher conditions at `debug`, and a
listener that threw at `error`. Configuration problems are *not* logged — they are thrown at
build time and delivered to `onReloadFailure` on reload, and logging them as well would
double-report the same event.

## Consequences

- Core's dependency set is now four artifacts, and this ADR is the record of why the fourth
  is there. The rule ADR-0005 actually protects — **no provider artifact, ever** — is
  untouched.
- Applications with no binding see SLF4J's own "no providers were found" notice once. That
  is upstream behaviour, identical to what LangChain4j itself produces.
- A future maintainer bumping LangChain4j must move `slf4j.version` with it if the
  transitive version changes. The enforcer fails the build if they do not, which is why the
  property carries a comment rather than a hope.
