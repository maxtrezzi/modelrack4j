# Phase 0 — verification tasks

These come first. They establish facts that design details depend on, and several of them
can invalidate an assumption a current ADR rests on — which is cheap to fix now and
expensive to fix after the code exists.

**Check upstream sources, not recollection.** Every task here exists because the answer
changes over time: module names move, artifacts get removed, interfaces get renamed. An
answer produced from memory is the failure this phase is designed to prevent.

Record what you find in the entry itself. A verification whose answer is not written down
has to be repeated.

---

### Task 0.1 — Pin the LangChain4j version

**Status:** Not started

Find the current stable LangChain4j release on Maven Central and pin it in the root POM as
`<langchain4j.version>`.

**Why it gates:** Tasks 0.2, 0.5, and 0.6 all ask "…for *which* version?" and cannot be
answered until this one is. Nothing in M0 can build without it.

**Done when:** a specific version is chosen and recorded, with the date checked.

---

### Task 0.2 — Verify the Java baseline

**Status:** Blocked by Task 0.1

Determine the Java version required by the pinned LangChain4j release — from its POM or
release notes, not from assumption — and set `maven.compiler.release` to match.

Records are used throughout the design, so the floor is 17 whatever LangChain4j allows.
If LangChain4j requires *more* than 17, it wins.

**Why it gates:** M0's skeleton cannot compile without it, and CI needs to know which JDKs
to build on.

**Done when:** `maven.compiler.release` is set and the reasoning recorded. Note that the
development machine currently runs JDK 21, which is not the same question.

---

### Task 0.3 — GLM module status

**Status:** Not started

Establish whether a maintained LangChain4j module for Zhipu GLM exists. History to verify:
`langchain4j-community-zhipu-ai` moved to the community repository around 1.0.0-alpha1 and
was reportedly removed in community 1.3.0 (upstream issue langchain4j/langchain4j#3606).

Two outcomes:
- A maintained module exists → use it.
- It does not → build the GLM factory on `langchain4j-open-ai` against Zhipu's
  OpenAI-compatible endpoint, accepting the loss of GLM-specific parameters.

**Why it gates:** M4, and the provider list in [ADR-0005](../adr/0005-provider-factory-spi-via-serviceloader.md).

**Blocks:** decision [D1](open-decisions.md#d1--glm-route-if-no-maintained-module-exists) —
the owner only needs to rule on the fallback if this task finds no maintained module.

**Done when:** the module's status is confirmed from the upstream repository and the route
chosen.

---

### Task 0.4 — Which Gemini module

**Status:** Not started

Two modules exist — `langchain4j-google-ai-gemini` and the newer `langchain4j-google-genai`.
Determine which upstream currently recommends, choose one, and record why.

**Why it gates:** M4, and the provider module list.

**Done when:** the choice is made and the reason written down, so it can be revisited if
upstream deprecates the winner.

---

### Task 0.5 — Confirm interface names

**Status:** Blocked by Task 0.1

Against the pinned version, confirm the exact names and packages of every type the public
API touches: `ChatModel`, `StreamingChatModel`, `ModerationModel`
(expected in `dev.langchain4j.model.moderation`), `ChatMemoryProvider`, and
`TokenCountEstimator`.

This matters more than it looks: LangChain4j renamed `ChatLanguageModel` to `ChatModel` and
redesigned request parameters. Names in this repo's documentation were written from the
plan, not from the pinned artifact.

**Why it gates:** the SPI signature in [ADR-0005](../adr/0005-provider-factory-spi-via-serviceloader.md),
the bundle contents in [ADR-0003](../adr/0003-bundle-holds-config-shaped-inputs-only.md),
and every code sample in the eventual README.

**Done when:** each name is confirmed against the artifact. Any mismatch is corrected
across `CLAUDE.md` and the ADRs in the same change.

---

### Task 0.6 — Provider capability matrix

**Status:** Blocked by Task 0.1

For the pinned version, determine which providers actually ship a `ModerationModel`
(expected: the OpenAI family only) and which ship a `TokenCountEstimator` (expected:
OpenAI; the others need checking).

**Why it gates:** these facts *are* the `validate()` implementations. Capability-aware
validation is a stated differentiator in [ADR-0002](../adr/0002-scope-to-langchain4j-llm-configuration.md),
and the claims in [ADR-0004](../adr/0004-expose-chatmemoryprovider.md) and
[ADR-0005](../adr/0005-provider-factory-spi-via-serviceloader.md) are written as
expectations, not verified fact.

**Amends on failure:** if moderation turns out to be available beyond the OpenAI family, or
token estimation is broader or narrower than expected, ADR-0004 and ADR-0005 need
amending.

**Done when:** a provider × capability table exists, sourced from the artifacts, and the
README capability matrix can be generated from it.

---

### Task 0.7 — Name and coordinates

**Status:** Partly done

The name `modelrack4j` is settled and collision-checked
([ADR-0011](../adr/0011-independent-name-and-deferred-wrapper.md)).

Still open: confirm the GitHub organisation/repository name and the `io.github.<owner>`
groupId are actually available before the skeleton fixes them into every POM.

**Why it gates:** M0. Coordinates are deliberately fixed early so no consumer POM has to
change later, which only works if they are available.

**Done when:** both are confirmed available and the concrete owner segment is recorded.

---

### Task 0.8 — Watch strategy spike

**Status:** Not started

A spike, not a design task: run code and observe real behaviour before implementing the
watcher. `WatchService` registers on directories, not files, and several deployment
patterns defeat naive implementations.

Verify on the target platforms:

1. Events for a specific filename can be filtered reliably out of directory-level events.
2. An editor writing via temp-file-then-rename — confirm it surfaces as `ENTRY_CREATE`
   rather than `ENTRY_MODIFY`, so the debounce must accept both.
3. A symlinked config path whose target is swapped atomically (the Kubernetes ConfigMap
   pattern). Naive watching misses this entirely. Decide between resolving to the real
   path, watching the link's directory, or falling back to polling.
4. macOS latency — the implementation there is polling-based internally. Measure it.

**Why it gates:** M3. It is also the evidence behind
[ADR-0013](../adr/0013-watch-directories-resolve-symlinks.md), which was written from
reasoning rather than measurement.

**Amends on failure:** if the ConfigMap case cannot be handled by resolving real paths, or
if macOS latency is bad enough to undermine the push-based claim in
[ADR-0002](../adr/0002-scope-to-langchain4j-llm-configuration.md), ADR-0013 needs
amending and the README's real-time claim needs qualifying.

**Done when:** all four behaviours are observed and written down, with the macOS latency
figure recorded as a number.
