# ADR-0033: Provider exceptions pass through untranslated; the swap boundary is construction, not invocation

- **Status:** Accepted
- **Date:** 2026-08-24
- **Supersedes:** —
- **Amends:** —

## Context

[P6](../tasks/post-v1.md#p6--the-integration-tests-against-live-apis) ran the integration
tests against all four live provider APIs for the first time. Three of the four runs failed
before they were made to pass, and the failures were more informative than the successes:

| Provider | Real condition | Exception thrown |
|---|---|---|
| OpenAI | account out of credit | `dev.langchain4j.exception.RateLimitException` |
| Gemini | model ID retired upstream | `dev.langchain4j.exception.ModelNotFoundException` |
| GLM | resource package out of credit | `dev.langchain4j.community.model.zhipu.ZhipuAiException` |

The first two are LangChain4j's semantic exception types, produced by its `ExceptionMapper`
from the HTTP status. The third is the community module's own type, thrown directly with no
mapper in the stack. It does extend `dev.langchain4j.exception.LangChain4jException` — read
from the artifact with `javap`, not assumed — so a catch-all still works, but nothing finer
does.

Read the first and third rows together. **The same real-world condition — no money on the
account — arrives as two different exception types depending on which provider the
configuration names.** GLM's message is additionally in Chinese
(`余额不足或无可用资源包，请充值。`), and its detail code is reachable only through a
provider-specific `getCode()`.

This matters here rather than upstream because of what this library claims. `ProviderSwap`
demonstrates an `ask()` method that names no provider, imports no provider type and contains
no branch, and switches from Anthropic to OpenAI on a file edit alone. That demonstration is
accurate. The question this ADR settles is how far it extends.

## Forces

**The pitch pulls towards translating.** A library whose headline is *change the provider by
editing a config file* invites the reader to conclude that no code changes when the provider
does. Leaving `catch (RateLimitException e)` silently dead after a swap to GLM is a sharp
edge in exactly the place the library advertises smoothness, and it fails quietly — the
catch block simply stops being entered.

**The architecture pulls hard the other way.** [ADR-0003](0003-bundle-holds-config-shaped-inputs-only.md)
fixes a bundle as the config-shaped *inputs* only: the registry hands back genuine LangChain4j
objects and gets out of the way. Translating an exception means being on the stack when the
model is invoked, which means standing between the caller and the `ChatModel` — a proxy. That
is more than [ADR-0011](0011-independent-name-and-deferred-wrapper.md) deferred to v2, and it
would apply to every call rather than only across a reload. The library would stop handing
out LangChain4j objects and start handing out wrappers that merely resemble them.

**A translation table would have to guess.** Is "out of credit" a rate-limit condition? OpenAI
returns HTTP 429 and LangChain4j maps it to `RateLimitException`, so upstream's answer is yes.
Zhipu's answer is a bespoke type. Any normalisation this project wrote would encode one of
those readings as the truth, per provider, per upstream release, and would be wrong the first
time either changed — with the failure showing up as a *miscategorised* error, which is worse
to debug than an unfamiliar one.

**The gap is upstream's, and upstream already owns the mechanism.** `ExceptionMapper` exists
and the stable-train modules use it; the community module does not. Papering over that here
would fork the semantics of a type this project does not own, and would keep doing so after
upstream fixed it.

**Saying nothing was the third option**, and the weakest: the asymmetry is real, a user will
meet it, and it is cheap to document once.

## Decision

**Provider exceptions raised during model invocation pass through untranslated.** The
library adds no exception hierarchy of its own on that path, and wraps no model call.

**The provider-portable contract is `dev.langchain4j.exception.LangChain4jException`.** All
four providers throw something beneath it. Any handling finer than that — `RateLimitException`,
`ModelNotFoundException`, `ZhipuAiException`, `getCode()` — is provider-specific and must be
written as such.

**The swap guarantee is stated at its true width: it covers construction, not invocation.**
Which objects exist, which provider builds them, with which credentials, model, timeout and
memory, are all config-shaped and swap freely. What a *failing* call throws belongs to the
provider. The README and the manual say so where a reader meets the claim, not in a footnote.

This changes no code. It fixes the boundary and stops it drifting.

**Configuration-time failures are unaffected and stay uniform.** `ConfigValidationException`
and `UnknownConfigurationException` are this library's own, are thrown identically whichever
provider is named, and remain the fail-fast contract
[ADR-0008](0008-fail-fast-validation-staged-build-atomic-swap.md) established. The split is
deliberate: **the library owns failures it can detect while building; the provider owns
failures that need the network.**

## Consequences

**Gained.** The bundle keeps holding real LangChain4j objects, with no proxy layer and no
per-call cost. Upstream improvements arrive for free: if the community module adopts
`ExceptionMapper`, GLM starts throwing mapped types and nothing here needs changing. The
library takes on no maintenance burden that grows with the provider count times the upstream
release cadence.

**Accepted cost.** Provider swapping is not quite as total as the demonstration suggests. An
application with provider-specific error handling has one place that a config-only swap does
not reach, and it fails silently rather than loudly. Documentation is the whole mitigation,
which is weaker than a compiler error — accepted knowingly, because the alternative costs the
architecture.

**Foreclosed.** A `modelrack4j` exception hierarchy for runtime failures. A translation table
mapping provider errors onto common types. Any `ChatModel` proxy introduced for the purpose of
catching what it throws.

**What a future contributor may be tempted to "simplify".** Adding a small wrapper that
catches provider exceptions and rethrows them as something uniform will look like a kindness
and reads as a few lines. It reintroduces the proxy this ADR exists to keep out, puts this
library on the stack of every model call it was designed to stay off, and makes the wrapper's
translation table a permanent maintenance obligation against four upstream projects on two
release trains. If it is ever genuinely wanted, it is a new ADR and a new module — not an
edit to core.
