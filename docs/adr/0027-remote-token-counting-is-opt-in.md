# ADR-0027: Token-window memory on a remote estimator is opt-in, not rejected and not merely warned

- **Status:** Accepted
- **Date:** 2026-08-21
- **Supersedes:** —
- **Amends:** —

## Context

[ADR-0021](0021-token-estimation-is-universal-but-two-cost-classes.md) established the
*capability model* — token estimation is three-valued, `ABSENT`/`LOCAL`/`REMOTE` — and
deliberately left the *policy* open, because each option changes the configuration schema,
which is the owner's to change. That was
[D3](../tasks/open-decisions.md#d3--token-window-memory-on-a-remote-estimator), and the
owner has now settled it.

The problem it addresses: `TokenWindowChatMemory` calls the estimator on eviction. Of the
four providers, only OpenAI's estimator counts locally (`jtokkit`). Anthropic's and Gemini's
are HTTP clients carrying an API key, a base URL and a timeout. So `memory.type =
token-window` on those providers puts a **billed, rate-limited, failure-prone network call
into ordinary conversation turns**, inside a component applications reasonably assume is
local bookkeeping. GLM ships no estimator at all
([ADR-0022](0022-glm-via-the-community-module-and-its-bom.md)).

Three options were on the table: reject outright, warn and proceed, or require an explicit
opt-in.

## Forces

- **Reject** is safest and matches this project's fail-fast posture
  ([ADR-0008](0008-fail-fast-validation-staged-build-atomic-swap.md)), but it is simply wrong
  for a user who wants accurate remote counts and has priced them in. A configuration library
  that forbids a legal upstream configuration is overreaching.
- **Warn** forbids nothing, but a log line is a weak signal for a *per-turn* cost. Warnings
  are read once during development and never again, and this one describes an ongoing
  monetary and latency cost that grows with traffic. It is the option most likely to be
  discovered from an invoice.
- **Opt-in** is fail-fast by default and escapable on purpose, which is the shape of the rest
  of the library — but it costs a configuration key, and
  [ADR-0010](0010-discriminators-only-with-two-real-variants.md) is deliberately hostile to
  keys that do not earn their place.
- **The key earns it**, and this is the crux. ADR-0010's target is *speculative* keys —
  discriminators invented for variants that do not exist yet. This key is not speculative:
  it marks a decision the user must actually make, it has two real states with different
  consequences today, and its absence cannot be inferred from anything else in the
  configuration. Without it the library must silently guess whether the user meant to accept
  a per-turn network call.

## Decision

**`memory.type = token-window` on a provider whose token estimation is `REMOTE` fails
validation unless the configuration explicitly opts in**, with:

```hocon
memory {
  type = token-window
  max-tokens = 2000
  allow-remote-token-counting = true    # required for anthropic and gemini; default false
}
```

**Default is `false`.** The failure message must name the provider, state that eviction will
make a billed network call per occurrence, and name the flag that permits it — a validation
error that does not explain the escape hatch has just made the flag undiscoverable.

**`ABSENT` is not escapable.** GLM has no estimator, so `token-window` fails there whatever
the flag says. The flag permits a *cost*; it cannot conjure a capability.

**On `LOCAL` providers the flag is permitted and has no effect.** It is not an error to set
it, because one configuration layer commonly spans several named blocks on different
providers, and making the key provider-conditional would force users to split layers to
satisfy a validator. This is a deliberate exception to fail-fast: the key is *inert*, not
*wrong*.

The three-valued capability from ADR-0021 is what makes this expressible — a boolean
"has estimator" could not tell `LOCAL` from `REMOTE` and so could not implement this rule at
all.

## Consequences

- The expensive configuration is unreachable by accident, and reachable on purpose in one
  line. Nobody discovers per-turn token-counting charges from an invoice.
- **One new configuration key**, scoped to the `memory` block, and the schema in the plan
  changes with it.
- **The validation message is part of the contract, not decoration.** A future change that
  shortens it to "token-window not supported for anthropic" removes the only signpost to the
  flag and silently converts opt-in back into reject. It needs a test asserting the message
  names the flag.
- **Do not "simplify" the inert-on-`LOCAL` rule into an error.** It looks like a missing
  validation. It exists so that a shared config layer covering an OpenAI name and an
  Anthropic name does not have to be split in two.
- M2 implements the check for Anthropic; M4 for Gemini and GLM. The rule lives in core's
  validation, driven by the capability the factory reports, so no provider module restates it.
