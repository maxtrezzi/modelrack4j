# ADR-0047: Redact the credential from `LlmConfig.toString()`

- **Status:** Accepted
- **Date:** 2026-09-03
- **Supersedes:** —
- **Amends:** —

## Context

`LlmConfig` is a public record with an `apiKey` component, and it is the type an application
reaches through `registry.get(name).config()`. The value it holds is the credential *after*
substitution: the file says `api-key = ${ANTHROPIC_API_KEY}`, and the record holds the key
itself. A record's generated `toString()` prints every component, so
`log.info("built {}", bundle.config())` writes the key into a log file, and so does any
`toString()` of an `LlmBundle`, which carries the config as a component.

Nothing in the library prints an `LlmConfig`. This was found by reading the record rather
than by observing a leak, and every log and print statement in the four modules and the five
examples was checked: none of them passes a config or a bundle to a formatter.

The project already applies this reasoning one step away. `ConfigSource.id()` is documented
as a label the library prints, with an explicit instruction not to put a secret in it, and
[ADR-0044](0044-store-a-layer-back-as-text-validated-before-it-is-stored.md) refuses to let
an application hand back an `LlmConfig` to be stored, precisely because that would write the
resolved secret into the layer. The component holding the credential had no equivalent guard.

`0.1.0` is published, so this changes the observable behaviour of a released artifact.

## Forces

- **A record's `toString()` is a debugging aid, not a contract.** Its exact format is
  unspecified, and nothing should parse it. That makes changing it cheap in a way that
  changing `equals` would not be.
- **The reload diff is record equality** ([ADR-0006](0006-named-configurations-with-per-name-diffing.md)),
  and the key is part of it. A rotated credential must count as a changed configuration, or a
  key rotation would be a configuration change that never took effect. So whatever is done
  here must not touch `equals` or `hashCode`.
- **Redacting the whole record was the cheap alternative and is worse.** Returning
  `"LlmConfig[SL]"` removes the leak and also removes the reason anyone prints a config. A
  description that is safe and useless gets replaced by hand-rolled logging that is neither.
- **Documenting the hazard instead of fixing it was the other alternative.** It is what the
  library does for `ConfigSource.id()`, because there the value is supplied by the
  implementer and the library cannot know what is in it. Here the library controls the
  component and knows exactly which one is the secret, so a note telling users to avoid the
  default `toString()` puts the work on every user to avoid a default that is wrong.
- **A leaked key is not recoverable.** A log line reaches a log aggregator, a backup and a
  support ticket. Weighed against a `toString()` whose format nobody may depend on, the
  asymmetry is total.

## Decision

`LlmConfig` overrides `toString()` to print every component except `apiKey`, which is
rendered as `apiKey=***`. The format otherwise follows what the record would have generated,
so the output stays recognisable.

`equals` and `hashCode` stay exactly as the record generates them, including the key.

The redaction is stated in the reference manual beside the record's shape, with the reason —
the value is the substituted credential, not the `${VAR}` — because the record's declaration
gives a reader no hint that one component is different from the others.

## Consequences

- An application can log a bundle or a config without leaking a credential, which is the
  behaviour a user would have assumed anyway.
- The reload diff is unchanged. A test asserts both halves together — that the key is absent
  from `toString()` and that two configs differing only in their key are still unequal — so a
  future "simplification" that pushes the redaction into `equals` fails rather than silently
  disabling reload on key rotation.
- **`toString()` no longer round-trips to the key**, which removes one way of debugging a
  wrong-credential problem. That is the point, and the config's own `apiKey()` accessor is
  still there for code that genuinely needs the value.
- This is a behaviour change in a published artifact. It is permitted — the project is `0.x`
  and the CHANGELOG reserves the right to break in a minor — and it is the safe direction:
  code that printed a config keeps compiling and keeps working, and only stops emitting a
  secret.
- It does not generalise by itself. Any future record that carries a credential needs the
  same treatment deliberately; the compiler will not ask.
