# ADR-0030: One `timeout` in the schema; providers map it onto their own client

- **Status:** Accepted
- **Date:** 2026-08-23
- **Supersedes:** —
- **Amends:** —

## Context

Three of the four provider modules take a single `timeout` on their builder, so the schema's
`timeout` key passed straight through and the question never came up. The fourth does not.
`ZhipuAiChatModel`'s builder has **four** timeouts — `callTimeout`, `connectTimeout`,
`readTimeout`, `writeTimeout` — and two of them, `callTimeout` and `writeTimeout`, are
annotated deprecated and marked for removal at `1.19.0-beta29`.

So `timeout = 30s` has no single obvious destination for GLM, and the general question behind
it is due an answer before M5 fixes the schema: does a provider whose client exposes more
knobs get to add configuration keys of its own?

## Forces

- **A per-provider block would be honest about the underlying client.** `glm { read-timeout,
  connect-timeout }` says exactly what it does. It also makes the schema a moving target that
  grows with every provider added, makes a config file non-portable between providers, and
  hands users four ways to express one intention.
- **The library's whole proposition is that a named block is provider-shaped input, not a
  provider SDK in HOCON** ([ADR-0002](0002-scope-to-langchain4j-llm-configuration.md),
  [ADR-0003](0003-bundle-holds-config-shaped-inputs-only.md)). A user swapping `provider =
  openai` for `provider = glm` should not have to rewrite their timeouts.
- **The mapping is not always faithful, and pretending otherwise would be worse.** One number
  cannot express four independent bounds. What it *can* express is the user's actual
  intention: a request that hangs must not hang forever.
- **Deprecated builder methods are not a foundation.** Writing to `callTimeout` today buys a
  compile warning now and a broken build on the next community release.

## Decision

**The schema keeps exactly one `timeout` key.** Provider modules map it onto whatever their
client exposes; the mapping lives in the factory, never in the configuration.

For GLM specifically, `timeout` is applied to **`connectTimeout` and `readTimeout`**, the two
that are not deprecated. `callTimeout` and `writeTimeout` are deliberately left unset.

A provider-specific configuration key is not added to solve a provider-specific API shape. If
a provider genuinely cannot be driven by the common schema, that is a reason to reconsider
supporting it, not a reason to grow the schema.

## Consequences

- Config files stay portable across providers, which is what makes the three-model scenario
  worth writing in the first place.
- **GLM has no overall call bound**, only per-phase ones. A response that arrives as a slow
  trickle of bytes, each within `readTimeout`, can outlast it. This is accepted: the failure
  mode `timeout` exists to prevent — a connection that hangs and never returns — is covered,
  and the alternative is a deprecated method.
- Revisit if the community module removes the deprecated pair and offers a supported
  whole-call timeout, or if a provider appears that the single key genuinely cannot drive.
- **Do not "complete" the GLM mapping by adding `callTimeout` and `writeTimeout` back.** They
  are marked for removal; setting them buys a deprecation warning today and a build failure
  on the next bump.
