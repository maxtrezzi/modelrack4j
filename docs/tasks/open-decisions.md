# Open decisions

Items waiting on the owner rather than on work. Do not resolve these unilaterally — each
one closes by writing an ADR (see [ADR-0001](../adr/0001-record-decisions-as-adrs.md)).

---

### D1 — GLM route if no maintained module exists

**Status:** Needs decision · **Blocked by:** [Task 0.3](phase-0-verification.md#task-03--glm-module-status)

Only live if Task 0.3 finds no maintained LangChain4j module for Zhipu GLM. In that case
the fallback is a `GlmProviderFactory` built on `langchain4j-open-ai` pointed at Zhipu's
OpenAI-compatible endpoint.

The trade-off to accept or reject: it works, but GLM-specific parameters are unreachable
through the OpenAI-shaped API, and the library would be depending on an endpoint
compatibility guarantee it does not control.

**Blocks:** M4.

**On settling:** write an ADR recording the route and the trade-off, and amend
[ADR-0005](../adr/0005-provider-factory-spi-via-serviceloader.md) if the provider set
changes.

---

### D3 — Token-window memory on a remote estimator

**Status:** Needs decision · **Raised by:** [Task 0.6](phase-0-verification.md#task-06--provider-capability-matrix)

[ADR-0021](../adr/0021-token-estimation-is-universal-but-two-cost-classes.md) established
that all four providers ship a `TokenCountEstimator`, but only OpenAI's counts locally.
Anthropic's and both Gemini ones make an HTTP call, with an API key and a timeout.

`TokenWindowChatMemory` calls the estimator on eviction. So `memory.type = token-window` on
those three providers puts a billed, rate-limited, failure-prone network call into ordinary
conversation turns — inside a component applications reasonably assume is local bookkeeping.

ADR-0021 fixes the *capability model* (three-valued, so validation can tell the cases apart)
but deliberately not the *policy*, because each option changes the config schema, which is
the plan's to change and not an ADR's:

- **Reject** — `validate()` fails the configuration. Safest, and wrong for anyone who
  genuinely wants accurate remote counts and has priced it in.
- **Warn** — build it, log loudly once. Nothing is forbidden, but a warning in a log is a
  weak signal for a per-turn cost, and this project's whole posture is fail-fast validation
  ([ADR-0008](../adr/0008-fail-fast-validation-staged-build-atomic-swap.md)).
- **Opt-in** — reject unless the config says so explicitly, e.g. a
  `memory.allow-remote-token-counting` flag. Fail-fast by default, escapable on purpose.
  Costs one schema key, and [ADR-0010](../adr/0010-discriminators-only-with-two-real-variants.md)
  is hostile to keys that do not earn their place.

**Blocks:** M2 (memory construction) and the `validate()` implementations in M4.

**On settling:** write an ADR; if the answer adds a config key, the schema in the plan
changes with it.

---

### D2 — Repository visibility

**Status:** Needs decision

Public from day one, or public at first release?

This became live rather than theoretical once the repository was initialised and its first
commit landed. Two things depend on it:

- **JitPack** requires a public repository. It is the intermediate distribution option if
  the library needs sharing before it reaches Maven Central.
- **The ADRs are written to be read.** Their value as a public rationale record — a large
  part of what makes an independent library credible
  ([ADR-0002](../adr/0002-scope-to-langchain4j-llm-configuration.md),
  [ADR-0011](../adr/0011-independent-name-and-deferred-wrapper.md)) — only materialises
  when someone outside the project can read them.

Nothing has left the machine: there is no remote configured. The decision is genuinely open
and reversible in one direction only — a repository can go from private to public, but what
has been public cannot be made unseen.

**On settling:** write an ADR, and if the answer is "public at first release", note what
triggers the switch so it does not drift.
