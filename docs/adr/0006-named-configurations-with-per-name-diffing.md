# ADR-0006: Named configurations, one merged snapshot, per-name diffing

- **Status:** Accepted
- **Date:** 2026-07-26 *(record backfilled; the decision itself predates it)*
- **Supersedes:** —

## Context

Applications rarely use one model. The motivating scenario runs three simultaneously — a
low-temperature worker, a high-temperature one, and a critic — and needs to address each
by a name that means something to the application, not to the vendor.

## Forces

- Two configurations frequently share a provider and differ only in parameters. Keying the
  registry by provider would make that unrepresentable.
- Names are the application's vocabulary. A registry keyed on anything else forces the
  caller to maintain its own mapping, which is the bookkeeping this library exists to
  remove.
- Named model configuration has prior art in Quarkus LangChain4j, where it is
  config-time only — evidence that the naming concept is sound, and a gap this library
  fills by adding runtime reload for plain Java.
- Rebuilding every bundle on every file event churns objects that did not change,
  discarding live HTTP clients for no reason.
- Reliable change detection needs a cheap, correct equality test on parsed configuration.

## Decision

Configuration is a map of named blocks. On any change, all files are re-merged in memory
into a single snapshot, then each named block is compared against its previous version by
**record equality**. Only blocks that actually differ are rebuilt; unchanged blocks carry
their existing instances into the new snapshot untouched.

Registry keys are configuration names, never provider names.

## Consequences

- Change detection is free and correct, because records supply `equals` derived from their
  components.
- Configuration must be parsed into immutable records — a requirement this shares with the
  fail-fast validation in ADR-0008, so it costs nothing extra.
- Unchanged bundles keep object identity across a reload, so callers holding one
  transiently are not disrupted by an edit elsewhere in the file.
- The per-name diff determines what is *rebuilt*; it does not weaken the atomicity
  guarantee, which remains snapshot-wide (ADR-0012).
- Records must contain everything semantically relevant to construction. A field left out
  of the record makes a real change invisible to the diff — the failure mode to watch for
  when extending the schema.
