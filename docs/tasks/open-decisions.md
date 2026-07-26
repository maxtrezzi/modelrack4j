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
