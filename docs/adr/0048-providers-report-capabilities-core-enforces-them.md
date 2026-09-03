# ADR-0048: Providers report capabilities; core enforces them

- **Status:** Accepted
- **Date:** 2026-09-03
- **Supersedes:** —
- **Amends:** —

## Context

`ProviderFactory` has carried two different kinds of method since M4 without saying so.
`tokenEstimation()` **reports** what a provider can do, and core turns that report into a
validation rule and its message
([ADR-0021](0021-token-estimation-is-universal-but-two-cost-classes.md),
[ADR-0027](0027-remote-token-counting-is-opt-in.md)). `validate()` lets a provider **enforce**
a rule itself. The SPI's own Javadoc already stated the preference — capability rules that
depend only on `tokenEstimation()` "are applied by core and must not be restated here" — but
only for that one capability.

Moderation went the other way. Three of the four factories carried a `validate()` body that
rejected `moderation.enabled = true`, and the three were identical apart from a hardcoded
provider name — a name each class already holds as its own `PROVIDER_ID` constant and did not
use. Core was not silent on the case either: `SnapshotLoader.requireProduced` already fails
when a configuration asks for moderation and the factory returns an empty `Optional`. So the
rule existed in four places, in two different wordings, and which message a user saw depended
on which provider they had named.

This came out of a review pass over all seven modules. The duplication is what was visible;
the asymmetry underneath it is what made it worth an ADR rather than a tidy-up.

## Forces

- **Duplication of a rule is not a tidiness problem, it is a divergence problem.** Three
  copies of a message stay in step only while somebody remembers to edit three files. The
  next capability with the same shape would have made it four.
- **`validate()` still has a job.** A provider can have a rule core cannot see — GLM's
  key format ([P25](../tasks/post-v1.md#p25--a-malformed-glm-key-fails-before-the-call-past-the-exception-guarantee)) is one waiting to be written. Removing the method
  would trade one problem for a worse one.
- **The SPI is published.** `0.1.0` is on Maven Central, so an interface method that is not
  `default` would break every implementer at compile time. That constrains the shape but not
  the decision.
- **The default value is the whole compatibility question**, and it was the only genuinely
  hard part. `false` reads better as a statement about the world — most providers do not
  moderate — and would silently start rejecting a third-party factory that does moderate and
  has not heard of the new method. `true` reads worse and breaks nobody: a factory that does
  not override it is treated exactly as it is treated today, and `requireProduced` still
  catches a missing model a moment later. Correct beat well-phrased.
- **A shared message helper in core was the smaller alternative.** It would have removed the
  duplicated string while leaving the rule itself in three modules — the copies would then
  agree on their wording and still each decide independently whether to run. It fixes the
  symptom and preserves the asymmetry.
- **Corroboration from the test suite.** `FakeProviderFactory` in core's test scope had
  already invented a `supportsModeration()` method for exactly this purpose, and drove its own
  `validate()` from it. The shape being adopted here is one the tests had independently
  arrived at.

## Decision

A capability a provider *has or lacks* is reported through a method on `ProviderFactory`, and
the rule that acts on it, together with the message the user reads, lives in core's
`SnapshotLoader.validateCapabilities`.

`supportsModeration()` is added to the SPI as a `default` method returning `true`. The three
providers that ship no `ModerationModel` override it to `false`; `OpenAiProviderFactory`
overrides it to `true` explicitly, because the default means "not stated" rather than "yes"
and the one provider that genuinely moderates should say so.

The three `validate()` bodies that rejected moderation become empty, keeping the method and a
comment saying why it is there. `validate()` is now defined as the place for a rule core
cannot see.

## Consequences

- Every provider that cannot moderate refuses the same configuration in the same words, and
  the message is one string in one file.
- The rejection now happens in core's capability check rather than in a provider, so it fires
  before `factory.validate(config)` and before any model is built. The user-visible message
  changed from "has no moderation model" to "ships no moderation model", which is the wording
  the three providers already used.
- **Two words carry a real distinction and must stay.** Core's capability check says *ships
  no* moderation model; `requireProduced` says *produced no*. They report different failures —
  a provider that declared it cannot moderate, versus one that said it could and then did not.
  The test that covers the first asserts on "ships no" for exactly this reason, and loosening
  it to "moderation" would let either failure satisfy it.
- A third-party factory compiled against `0.1.0` keeps working unchanged, and keeps the
  behaviour it has today.
- The cost is a defaulted method whose default is chosen for compatibility rather than for
  truth. A reader who takes `supportsModeration()` at face value on a factory that never
  overrode it will be told `true` about a provider that may not moderate at all. That is why
  the fallback in `requireProduced` is load-bearing and must not be removed as redundant: it
  is what makes the permissive default safe.
- The next capability of this shape has a pattern to follow, and `validate()` has a narrower
  and more honest job description.
