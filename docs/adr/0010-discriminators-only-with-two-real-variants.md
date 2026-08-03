# ADR-0010: Discriminated variants only where two real variants exist today

- **Status:** Accepted — estimator-availability premise amended by [ADR-0021](0021-token-estimation-is-universal-but-two-cost-classes.md)
- **Date:** 2026-07-26 *(record backfilled; the decision itself predates it)*
- **Supersedes:** —

## Context

Some configuration blocks select between implementations rather than merely setting
values — memory is the obvious case. The mechanism for that is a discriminator field
naming the variant. The question is how liberally to use it, since a discriminator is
easy to add and permanent once published.

## Forces

- The mechanism itself is right and well-precedented; Jackson's polymorphic
  deserialization works the same way.
- Every discriminator multiplies work: more schema, more validation paths, more
  documentation, more test combinations, and one more thing that can be set to a value
  nobody implemented.
- A discriminator with exactly one legal value is pure overhead — it asks the user a
  question with one answer.
- Speculative discriminators are worse than absent ones. They lock a naming scheme in
  before the second variant exists to show whether the scheme fits it.
- Variant names are read by an operator at three in the morning. `message-window` tells
  them something; `1` does not, and a numeric or opaque discriminator forces a
  documentation lookup during an incident.

## Decision

A discriminator is introduced only when at least two real variants exist **today**. In v1
that is exactly one: `memory.type`, with values `message-window` and `token-window`.
The latter requires a provider offering a `TokenCountEstimator`, validated per provider
(ADR-0005).

Discriminator values are meaningful words, never numbers or codes.

## Consequences

- A lean schema, with each option earning its place.
- Deferral is genuinely free here: the SPI and schema both admit new variants without
  breaking existing configuration, so waiting costs nothing and buys real information
  about how the second variant should be named.
- Configuring `token-window` against a provider with no estimator fails at load with a
  clear message rather than at first use.
- The extension path is documented, so "why is there no `type` here?" has an answer that
  is a policy rather than an oversight.
