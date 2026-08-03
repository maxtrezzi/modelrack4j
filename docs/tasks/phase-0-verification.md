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

**Status:** Done — checked 2026-07-28 against Maven Central

Find the current stable LangChain4j release on Maven Central and pin it in the root POM as
`<langchain4j.version>`.

**Why it gates:** Tasks 0.2, 0.5, and 0.6 all ask "…for *which* version?" and cannot be
answered until this one is. Nothing in M0 can build without it.

**Done when:** a specific version is chosen and recorded, with the date checked.

#### Found

**Pinned version: `1.18.0`**, the current stable release. Published **2026-07-17**,
confirmed twice — Central's `Last-Modified` on the artifact and the upstream GitHub
release's `published_at`. It was eleven days old when checked, not fresh off the press.

> The `<lastUpdated>` field in `maven-metadata.xml` is *not* a release date — it records
> when Central regenerated the index, and read naively it makes every artifact look like it
> shipped today. Use `Last-Modified` on the artifact, or the upstream release.

**Upstream ships two version lines, and one property cannot express that.** This is the
finding that matters, and it contradicts the single-`<langchain4j.version>` premise above.
`langchain4j-bom:1.18.0` declares:

| Property | Value | Covers |
|---|---|---|
| `langchain4j.stable.version` | `1.18.0` | `langchain4j-core`, `-open-ai`, `-anthropic`, `-google-ai-gemini` |
| `langchain4j.beta.version` | `1.18.0-beta28` | `-google-genai`, and other not-yet-stabilised integrations |

Verified per artifact: `langchain4j-core`, `-open-ai`, `-anthropic` and `-google-ai-gemini`
all resolve at `1.18.0` (HTTP 200 on the POM); `langchain4j-google-genai` has **no** plain
`1.18.0` (HTTP 404) and its newest release is `1.18.0-beta28`.

**Consequence: import the BOM rather than hand-pinning artifacts** — it manages all 115
artifacts across both lines and keeps the two in step on every bump. Recorded as
[ADR-0018](../adr/0018-manage-langchain4j-versions-via-bom.md); M0 implements it.

#### Hands to other tasks

- **[Task 0.2](#task-02--verify-the-java-baseline), [0.5](#task-05--confirm-interface-names),
  [0.6](#task-06--provider-capability-matrix)** — unblocked; the version they each asked
  "for which version?" about is `1.18.0`.
- **[Task 0.4](#task-04--which-gemini-module)** — a heavy input, but not the answer:
  `-google-ai-gemini` is stable while the newer `-google-genai` is still beta after 28
  betas. 0.4 asks what upstream *recommends*, which this does not establish.
- **[Task 0.3](#task-03--glm-module-status)** — no zhipu or GLM artifact appears anywhere
  in the BOM's 115 entries, consistent with the move to the community repository. Attempts
  to browse `dev/langchain4j/community/` on Central returned nothing, which is
  **inconclusive** rather than negative — Central does not reliably serve directory
  listings. 0.3 still has to check the community repository properly.

---

### Task 0.2 — Verify the Java baseline

**Status:** Done — checked 2026-08-03 against the artifacts, upstream POM and upstream docs

Determine the Java version required by the pinned LangChain4j release — from its POM or
release notes, not from assumption — and set `maven.compiler.release` to match.

Records are used throughout the design, so the floor is 17 whatever LangChain4j allows.
If LangChain4j requires *more* than 17, it wins.

**Why it gates:** M0's skeleton cannot compile without it, and CI needs to know which JDKs
to build on.

**Done when:** `maven.compiler.release` is set and the reasoning recorded. Note that the
development machine currently runs JDK 21, which is not the same question.

#### Found

**LangChain4j `1.18.0` requires Java 17.** It does not require more than 17, so the
project's own floor stands and `maven.compiler.release` is **17** — recorded as
[ADR-0019](../adr/0019-target-java-17.md), which M0 implements.

Confirmed three independent ways, since one source alone would not have settled it:

| Source | Evidence |
|---|---|
| Bytecode in the published artifacts | major version **61** (= Java 17) on all 1006 classes across `langchain4j-core`, `-open-ai`, `-anthropic`, `-google-ai-gemini` and `-google-genai:1.18.0-beta28`; **no** `META-INF/versions` overlays in any of them |
| `langchain4j-parent/pom.xml` at tag `1.18.0` | `<java.version>17</java.version>` with `<maven.compiler.release>${java.version}</maven.compiler.release>` |
| `docs.langchain4j.dev` | "The minimum supported JDK version is 17." |

> The published `langchain4j-core-1.18.0.pom` on Central is **flattened** — no parent, no
> `<build>` section, no compiler configuration. Reading the released POM alone cannot answer
> this question; the answer is in the upstream source POM and in the bytecode.

**Transitive dependencies impose no higher floor.** `jackson-databind:2.22.1`,
`jackson-core:2.22.1`, `jackson-annotations:2.22`, `slf4j-api:2.0.18` and `jspecify:1.0.0`
all carry Java 8 (major 52) base classes. Several are multi-release jars with 9/11/17/21
overlays, which raise the version they *exploit*, never the version they *require*.

#### Hands to other tasks

- **[M0](milestones.md#m0--skeleton-and-ci)** — set `maven.compiler.release` to 17 and build
  the CI matrix on JDK **17 and 21** per ADR-0019. The 17 leg is what stops the floor from
  rotting; building only on 21 would not detect a Java 21 API creeping in.
- **Re-check on every LangChain4j bump.** ADR-0018 makes the bump one line, which is exactly
  why a raised upstream baseline could ride in unnoticed.

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

**Status:** Done — checked 2026-08-03 against the `1.18.0` artifacts; one mismatch found and corrected

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

#### Found

Read from the `1.18.0` artifacts themselves, not from documentation.

| Type | Fully-qualified name | Artifact |
|---|---|---|
| `ChatModel` | `dev.langchain4j.model.chat.ChatModel` | `langchain4j-core` |
| `StreamingChatModel` | `dev.langchain4j.model.chat.StreamingChatModel` | `langchain4j-core` |
| `ModerationModel` | `dev.langchain4j.model.moderation.ModerationModel` | `langchain4j-core` |
| `TokenCountEstimator` | `dev.langchain4j.model.TokenCountEstimator` | `langchain4j-core` |
| `ChatMemory` | `dev.langchain4j.memory.ChatMemory` | `langchain4j-core` |
| `ChatMemoryStore` | `dev.langchain4j.store.memory.chat.ChatMemoryStore` | `langchain4j-core` |
| **`ChatMemoryProvider`** | `dev.langchain4j.memory.chat.ChatMemoryProvider` | **`langchain4j` (aggregate)** |
| `MessageWindowChatMemory`, `TokenWindowChatMemory` | `dev.langchain4j.memory.chat.*` | **`langchain4j` (aggregate)** |

Two corrections fall out of that table.

**1. `ChatMemoryProvider` is not in `langchain4j-core`.** This contradicted ADR-0005's
two-dependency rule directly — ADR-0004 requires the type, ADR-0005 forbade the artifact
carrying it. Resolved by [ADR-0020](../adr/0020-core-depends-on-langchain4j-aggregate.md):
core takes a third dependency on the `langchain4j` aggregate, with `opennlp-tools` excluded.
The exclusion was verified, not assumed — `opennlp` is referenced by exactly one class in
that jar, `DocumentBySentenceSplitter`, which is out-of-scope RAG splitting.

**2. `TokenCountEstimator` sits in `dev.langchain4j.model`**, not in a `.chat` or `.tokens`
sub-package as the plan's prose implied.

**`ChatLanguageModel` is gone, not deprecated.** No such type exists anywhere in `1.18.0`;
the rename to `ChatModel` is complete. Any sample written from memory against the old name
will not compile, which is the failure mode this task existed to prevent
([ADR-0011](../adr/0011-independent-name-and-deferred-wrapper.md) anticipated the churn).

Also confirmed, since the SPI touches them: the request/response types are
`dev.langchain4j.model.chat.request.ChatRequest` / `ChatRequestParameters` /
`DefaultChatRequestParameters` and `dev.langchain4j.model.chat.response.ChatResponse` /
`StreamingChatResponseHandler`.

#### Hands to other tasks

- **[Task 0.6](#task-06--provider-capability-matrix)** — the two capability types are now
  named exactly; 0.6 asks which providers implement them.
- **M0** — core's POM has three dependencies, not two, and the exclusion must be re-verified
  on every LangChain4j bump.

---

### Task 0.6 — Provider capability matrix

**Status:** Not started — unblocked by Task 0.1 (pinned `1.18.0`)

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
