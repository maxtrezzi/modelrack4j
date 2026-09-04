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

**Status:** Done — pinned `1.20.0`; first checked 2026-07-28, re-pinned 2026-08-20 and again
2026-09-04, all against Maven Central

Find the current stable LangChain4j release on Maven Central and pin it in the root POM as
`<langchain4j.version>`.

**Why it gates:** Tasks 0.2, 0.5, and 0.6 all ask "…for *which* version?" and cannot be
answered until this one is. Nothing in M0 can build without it.

**Done when:** a specific version is chosen and recorded, with the date checked.

#### Found

**Version pinned at this check: `1.18.0`**, then the current stable release — superseded by
the [re-pin below](#re-verified-and-re-pinned--2026-08-20). Published **2026-07-17**,
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
  "for which version?" about is `1.20.0` since the re-pins below, neither of which changed
  any of their answers.
- **[Task 0.4](#task-04--which-gemini-module)** — a heavy input, but not the answer:
  `-google-ai-gemini` is stable while the newer `-google-genai` is still beta after 28
  betas. 0.4 asks what upstream *recommends*, which this does not establish.
- **[Task 0.3](#task-03--glm-module-status)** — no zhipu or GLM artifact appears anywhere
  in the BOM's 115 entries, consistent with the move to the community repository. Attempts
  to browse `dev/langchain4j/community/` on Central returned nothing, which is
  **inconclusive** rather than negative — Central does not reliably serve directory
  listings. 0.3 still has to check the community repository properly.

#### Re-verified and re-pinned — 2026-08-20

**The pin is now `1.19.0`**, published **2026-08-14**. `1.18.0` had been superseded twice
while unused: `1.18.1` on 2026-07-29, then `1.19.0`. `langchain4j-bom`'s
`maven-metadata.xml` gives `<latest>` and `<release>` as `1.19.0`; dates are `Last-Modified`
on the artifacts, per the caveat above.

This is the one-line BOM-coordinate bump [ADR-0018](../adr/0018-manage-langchain4j-versions-via-bom.md)
was designed to make routine, so that ADR stands as written — only the specific version in
its Decision section is superseded by this entry. No ADR is amended, because nothing the
pin gates moved.

**The two-line structure is unchanged.** `langchain4j-bom:1.19.0` declares
`langchain4j.stable.version` = `1.19.0` and `langchain4j.beta.version` = `1.19.0-beta29`.
`langchain4j`, `-core`, `-open-ai`, `-anthropic` and `-google-ai-gemini` all resolve at
plain `1.19.0` (HTTP 200 on the POM); `langchain4j-google-genai` is still beta-only, now
`1.19.0-beta29`. Task 0.4's stable-vs-beta input therefore reads the same as before.

Everything the pin gates was re-checked against the `1.19.0` artifacts:

| Gated by the pin | Re-checked against | Result |
|---|---|---|
| [Task 0.2](#task-02--verify-the-java-baseline) — Java baseline | bytecode of `langchain4j-core-1.19.0` and `langchain4j-1.19.0` | major **61** (= Java 17) on all 673 classes, no `META-INF/versions` overlays — `release` 17 stands |
| [Task 0.5](#task-05--confirm-interface-names) — interface names | both jars' entry listings | all eight types unmoved; `ChatMemoryProvider`, `MessageWindowChatMemory`, `TokenWindowChatMemory` still aggregate-only; `TokenCountEstimator` still in `dev.langchain4j.model`; `ChatLanguageModel` still absent |
| [ADR-0020](../adr/0020-core-depends-on-langchain4j-aggregate.md) — the `opennlp` exclusion | `langchain4j-1.19.0.pom` | `org.apache.opennlp:opennlp-tools` still a compile-scope dependency of the aggregate, so the exclusion is still required |
| [Task 0.6](#task-06--provider-capability-matrix) — capability matrix | `-open-ai`, `-anthropic`, `-google-ai-gemini` jars | unchanged: `OpenAiModerationModel` + `OpenAiTokenCountEstimator`; Anthropic and Gemini each ship an estimator and no moderation model |

**Not re-checked:** `langchain4j-google-genai:1.19.0-beta29` was confirmed to exist but its
capability row was not re-read, and Task 0.2's re-check covered the two artifacts core
depends on rather than all five. Both are on the beta line or already-verified providers,
and neither gates M0 — but they are gaps in this re-verification, not silence.

**Re-check this on every bump.** Task 0.2's "Hands to other tasks" already says so; this
entry is what that looks like when done.

#### Re-pinned to 1.20.0 — 2026-09-04

**The pin is now `1.20.0`**, published **2026-09-04**, with the community train at
`1.20.0-beta30`. The two-line structure is unchanged, and everything the pin gates was
re-checked against the `1.20.0` artifacts: the Java baseline, the interface names and the
capability matrix all read exactly as they did at `1.19.0`. The full tables, and the two
things that did move, are in
[P34](post-v1.md#p34--langchain4j-1200-and-the-jar-the-aggregate-started-bringing) — the
aggregate started bringing a jar of its own, and the pin needed a `jspecify` pin beside it
to keep dependency convergence green.

---

### Task 0.2 — Verify the Java baseline

**Status:** Done — checked 2026-08-03 against the artifacts, upstream POM and upstream docs;
re-verified at `1.19.0` on 2026-08-20 ([Task 0.1](#re-verified-and-re-pinned--2026-08-20))
and at `1.20.0` on 2026-09-04 ([P34](post-v1.md#p34--langchain4j-1200-and-the-jar-the-aggregate-started-bringing))

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

**Status:** Done — checked 2026-08-20 against Maven Central, the community repository and
upstream issue #3606; the premise was wrong and the module is maintained

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

#### Found

**The module was never removed, and it is current.** `dev.langchain4j:langchain4j-community-zhipu-ai`
is at **`1.19.0-beta29`**, published **2026-08-19** — the day before this check — and it
tracks the mainline release train version for version, all 31 releases from `1.0.0-alpha1`
to today. It lives at `models/langchain4j-community-zhipu-ai/` in the `langchain4j-community`
repository. **Route: use it** ([ADR-0022](../adr/0022-glm-via-the-community-module-and-its-bom.md)).

**The premise in this task was wrong, and the reason is worth keeping.** Upstream issue
langchain4j/langchain4j#3606 asked why the module was "removed in 1.3.0". The project lead
answered it the same day, 2025-08-28: *"They were not removed, they are released under
`1.3.0-beta9` version."* Community modules ship **only** beta-suffixed versions, so a plain
`1.3.0` 404s and looks like a deletion. Identical in shape to `langchain4j-google-genai`
having no plain `1.18.0` (Task 0.1) — the third time this repo has met the beta-suffix trap.

> **Two dead ends, recorded so they are not repeated.** The group id is **`dev.langchain4j`**,
> not `dev.langchain4j.community` — the latter 404s, which is what made Task 0.1's browse
> attempt "inconclusive". And `search.maven.org`'s `latestVersion` field reported
> `1.0.0-beta5`, which is **stale by 30 releases** and would have confirmed the abandonment
> story outright. `maven-metadata.xml` is the source of truth; the search index is not.

**What it ships**, read from the `1.19.0-beta29` jar:

| Capability | Present? |
|---|---|
| `ChatModel` | ✅ `ZhipuAiChatModel`, with a native `ZhipuAiChatRequestParameters` |
| `StreamingChatModel` | ✅ `ZhipuAiStreamingChatModel` |
| `ModerationModel` | ❌ |
| `TokenCountEstimator` | ❌ |

Also ships `ZhipuAiEmbeddingModel` and `ZhipuAiImageModel`, both out of scope
([ADR-0003](../adr/0003-bundle-holds-config-shaped-inputs-only.md)). Bytecode is major 61
(Java 17), consistent with Task 0.2. Compile dependencies: `langchain4j-core` at the
**stable** `1.19.0` — the beta suffix describes the wrapper, not the core it binds to —
plus `langchain4j-http-client-jdk`, `io.jsonwebtoken:jjwt` and `com.google.guava:guava`.

**It is not in `langchain4j-bom`** — zero matches across that BOM's 116 artifacts. A separate
`langchain4j-community-bom` manages it, at the same `1.19.0-beta29`. That is the finding with
teeth, and ADR-0022 resolves it: the root POM imports **two** BOMs, bumped together.

#### Hands to other tasks

- **[D1](open-decisions.md#d1--glm-route-if-no-maintained-module-exists)** — closes without a
  ruling. It was only live if no maintained module existed, and one does.
- **[Task 0.6](#task-06--provider-capability-matrix)** — gains a fifth row: GLM has neither
  capability. It is the first genuine `ABSENT` for
  [ADR-0021](../adr/0021-token-estimation-is-universal-but-two-cost-classes.md)'s
  three-valued token-estimation enum, which until now had no non-hypothetical case.
- **[M4](milestones.md#m4--gemini-and-glm)** — unblocked on the GLM side.
  `GlmProviderFactory.validate()` rejects `moderation` and `memory.type = token-window`.
- **[Task 0.4](#task-04--which-gemini-module)** — indirectly informed: beta-suffixed does not
  mean unmaintained, which is a live input to the `-google-genai` question.

---

### Task 0.4 — Which Gemini module

**Status:** Done — checked 2026-08-20 against Maven Central, the artifacts, upstream docs and
issue #4383; there are three modules, not two

Two modules exist — `langchain4j-google-ai-gemini` and the newer `langchain4j-google-genai`.
Determine which upstream currently recommends, choose one, and record why.

**Why it gates:** M4, and the provider module list.

**Done when:** the choice is made and the reason written down, so it can be revisited if
upstream deprecates the winner.

#### Found

**Chosen: `langchain4j-google-ai-gemini`**, the stable module
([ADR-0023](../adr/0023-gemini-via-the-stable-google-ai-gemini-module.md)).

**There are three modules, and the third is why this looked confusing:**

| Module | Line | Transport | Docs status |
|---|---|---|---|
| **`langchain4j-google-ai-gemini`** | **stable `1.19.0`** | own HTTP client on `langchain4j-http-client-jdk` — **no Google SDK** | primary Gemini integration; no deprecation notice |
| `langchain4j-google-genai` | beta `1.19.0-beta29` | `com.google.genai:google-genai:1.63.0` | *"currently marked as **Experimental**. The API and implementation are subject to change"* |
| `langchain4j-vertex-ai-gemini` | beta `1.19.0-beta29` | Vertex AI SDK | no deprecation notice on its page |

**The "migrate to `-google-genai`" advice is real but about a different module.** Google
stopped supporting the Vertex AI SDK after June 2026; upstream issue langchain4j/langchain4j#4383
proposed a new Gen AI SDK module and *"gradually deprecate Vertex AI–specific bindings"*, and
was closed 2026-05-15. `-google-genai` is that replacement — **for `-vertex-ai-gemini`**.

**It does not reach `-google-ai-gemini`, which has no Google SDK in it.** Its compile deps are
`langchain4j-core`, `langchain4j-http-client[-jdk]` and Jackson; it speaks the Gemini
Developer API over REST directly. Verified from its POM, not inferred.

> **This is the trap to remember.** A web search for "which LangChain4j Gemini module" returns
> "migrate to google-genai" with confident supporting detail, and applying it here would swap a
> stable module for an Experimental one on the strength of a deprecation that does not touch it.

**Evidence the stable module is current, not legacy:** 60 releases, shipping in lockstep with
mainline (`1.17.0` 2026-06-26, `1.18.0` 2026-07-17, `1.19.0` 2026-08-14), documenting
`gemini-3-pro-preview`, and **zero `@Deprecated` markers** across all three Gemini artifacts.

**Capability parity**, so the choice is reversible: both ship `ChatModel`,
`StreamingChatModel` and a `TokenCountEstimator`. `-google-genai` additionally drags in the
Google SDK and `jackson-module-kotlin` — and so the Kotlin standard library.

**They target different APIs.** `-google-ai-gemini` is the Gemini Developer API (API key);
`-google-genai` wraps a unified SDK also covering Vertex AI, which needs Google Cloud
credentials and a project — not the API-key-shaped config this library expresses.

#### Hands to other tasks

- **[M4](milestones.md#m4--gemini-and-glm)** — unblocked on the Gemini side.
  `GeminiProviderFactory` builds on the stable module; token estimation stays `REMOTE`
  per [ADR-0021](../adr/0021-token-estimation-is-universal-but-two-cost-classes.md).
- **[Task 0.6](#task-06--provider-capability-matrix)** — its `-google-genai` row describes a
  module this project does not use. The row stays as recorded fact; the used module is the
  `-google-ai-gemini` row.
- **Re-check on every bump**, per ADR-0023's two revisit triggers: `-google-genai` leaving
  Experimental, or `-google-ai-gemini` gaining a deprecation notice.

---

### Task 0.5 — Confirm interface names

**Status:** Done — checked 2026-08-03 against the `1.18.0` artifacts; one mismatch found and
corrected; re-verified at `1.19.0` on 2026-08-20 ([Task 0.1](#re-verified-and-re-pinned--2026-08-20))
and at `1.20.0` on 2026-09-04 ([P34](post-v1.md#p34--langchain4j-1200-and-the-jar-the-aggregate-started-bringing))

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

**Status:** Done — checked 2026-08-03 against the `1.18.0` artifacts; token-estimation
expectation refuted; re-verified at `1.19.0` on 2026-08-20 ([Task 0.1](#re-verified-and-re-pinned--2026-08-20))
and at `1.20.0` on 2026-09-04 ([P34](post-v1.md#p34--langchain4j-1200-and-the-jar-the-aggregate-started-bringing)); GLM row added at M4 on 2026-08-23

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

#### Found

The matrix for `1.18.0`, read from the artifacts. **This is the source the README capability
table is generated from.**

| Provider | `ModerationModel` | `TokenCountEstimator` | Counting mechanism |
|---|---|---|---|
| `langchain4j-open-ai` | ✅ `OpenAiModerationModel` | ✅ `OpenAiTokenCountEstimator` | **local** — `jtokkit`, a declared compile dependency |
| `langchain4j-anthropic` | ❌ | ✅ `AnthropicTokenCountEstimator` | **remote** — HTTP to `/v1/…count_tokens` |
| `langchain4j-google-ai-gemini` | ❌ | ✅ `GoogleAiGeminiTokenCountEstimator` | **remote** — HTTP to `countTokens` |
| `langchain4j-google-genai` | ❌ | ✅ `GoogleGenAiTokenCountEstimator` | **remote** — HTTP |

**Moderation: expectation confirmed.** OpenAI only. The bundle's `Optional<ModerationModel>`
is permanently empty for three of the four providers.

**Token estimation: expectation refuted.** The task expected "OpenAI; the others need
checking" — in fact *all four* ship an estimator, so availability does not vary at all.

**What varies is the mechanism, and the constructor signatures prove it** rather than
suggesting it:

- `OpenAiTokenCountEstimator` is constructed from a model name and nothing else.
- The other three take a builder carrying `apiKey`, `baseUrl`, `timeout` and
  `httpClientBuilder`. They are HTTP clients.

This is consequential because `TokenWindowChatMemory` calls the estimator on eviction, so on
three of four providers ordinary conversation turns make a billed, rate-limited, network-
dependent call inside what the application treats as in-memory bookkeeping.

Recorded as [ADR-0021](../adr/0021-token-estimation-is-universal-but-two-cost-classes.md):
the SPI's token-estimation capability is three-valued (`ABSENT`/`LOCAL`/`REMOTE`), not
boolean. A boolean would return true for all four and bless every configuration — the check
would exist and catch nothing.

#### Completed at M4 — the GLM row the matrix never had

The table above covers the four modules Task 0.6 was written against, and
`langchain4j-community-zhipu-ai` is not one of them: at the time it was still an open
question ([D1](open-decisions.md#d1--glm-route-if-no-maintained-module-exists)) whether GLM
would come from a community module at all. Read from the `1.19.0-beta29` artifact on
2026-08-23, by listing its classes:

| Provider | `ModerationModel` | `TokenCountEstimator` | Counting mechanism |
|---|---|---|---|
| `langchain4j-community-zhipu-ai` | ❌ | ❌ **none at all** | — |

**GLM is the first provider with no estimator of any kind**, which makes it the first one
where `TokenEstimation.ABSENT` is a live value rather than a defensive branch. Token-window
memory is *unavailable* there, not merely expensive, so
[ADR-0027](../adr/0027-remote-token-counting-is-opt-in.md)'s opt-in flag must not smuggle it
through — the flag covers a cost, not an absence. A test asserts exactly that.

The module ships `ZhipuAiChatModel`, `ZhipuAiStreamingChatModel`, `ZhipuAiEmbeddingModel` and
`ZhipuAiImageModel`; the last two are out of scope ([ADR-0003](../adr/0003-bundle-holds-config-shaped-inputs-only.md)).
Its `shared.SensitiveFilter` looks like moderation from its name and is not — it is a plain
DTO with `getRole`/`setRole`, unrelated to `ModerationModel`.

**One further fact, not a capability but worth recording:** `ZhipuAiChatModel.provider()`
returns `ModelProvider.OTHER`. An application routing on `ChatModel.provider()` cannot tell a
GLM model from any other community module's, so the registry name is the only reliable
discriminator. Pinned by a test.

**Gemini re-checked at `1.19.0` while building M4** and unchanged: no moderation,
`GoogleAiGeminiTokenCountEstimator` present and remote.

#### Hands to other tasks

- **[D3](open-decisions.md#d3--token-window-memory-on-a-remote-estimator)** — new, and needs
  the owner: what `validate()` should *do* about token-window memory on a remote estimator.
  Reject, warn, or opt-in — it changes the config schema, so ADR-0021 deliberately does not
  decide it.
- **[Task 0.4](#task-04--which-gemini-module)** — does not separate the two Gemini modules;
  both are moderation-less and remote-counting, so capability is not a tiebreaker.
- **[D1](open-decisions.md#d1--glm-route-if-no-maintained-module-exists)** — a GLM factory
  built on `langchain4j-open-ai` would inherit OpenAI's *tokenizer* by accident of shape.
  Whether GLM's tokenization matches OpenAI's is not something the endpoint's compatibility
  guarantee covers; `ABSENT` is the honest reporting there.
- **Re-verify on every LangChain4j bump.** This is a fact about `1.18.0`, not a permanent
  property.

---

### Task 0.7 — Name and coordinates

**Status:** Done — checked 2026-08-20 against Maven Central, GitHub and Sonatype's own
namespace documentation

The name `modelrack4j` is settled and collision-checked
([ADR-0011](../adr/0011-independent-name-and-deferred-wrapper.md)).

Still open: confirm the GitHub organisation/repository name and the `io.github.<owner>`
groupId are actually available before the skeleton fixes them into every POM.

**Why it gates:** M0. Coordinates are deliberately fixed early so no consumer POM has to
change later, which only works if they are available.

**Done when:** both are confirmed available and the concrete owner segment is recorded.

#### Found

**Coordinates fixed: `io.github.maxtrezzi:modelrack4j-*`**
([ADR-0025](../adr/0025-fix-coordinates-under-io-github-maxtrezzi.md)).

| Check | Result |
|---|---|
| `io.github.maxtrezzi` on Central | **unused** — `g:"io.github.maxtrezzi"` returns 0; free-text `modelrack` returns 0 across all groups |
| Artifact name `modelrack4j-*` | **no collision** anywhere on Central |
| GitHub repository | `maxtrezzi/modelrack4j` exists — **user** account (not an organisation), Apache-2.0, default branch `main` |
| Namespace eligibility | **automatic** — Sonatype Central grants `io.github.<github username>` on GitHub signup with no verification steps |

> Central's directory browse for `/io/github/maxtrezzi/` returns 404, which per Task 0.1 is
> **inconclusive** rather than negative. The two search queries are what establish the
> namespace is unused; the 404 is not evidence.

**The finding that shaped the decision.** Sonatype's documentation states: *"we only support
the GitHub username that you used to sign up, so `io.github.<github organization name>` is
not available as an automatically registered namespace."* So the obvious future tidy-up —
create a `modelrack4j` organisation and move the groupId to match — costs manual verification
**and** breaks every consumer POM. ADR-0025 therefore decouples the two: if the repository
ever moves to an organisation, the groupId stays `io.github.maxtrezzi`.

**Not claimed, only confirmed available.** No Sonatype account exists yet. Publishing remains
deferred; this task establishes that the coordinates M0 bakes in will still be usable when it
is not.

#### Hands to other tasks

- **[M0](milestones.md#m0--skeleton-and-ci)** — unblocked. Write
  `io.github.maxtrezzi:modelrack4j-parent`, `-core` and `-provider-*` into the skeleton.
- **[D2](open-decisions.md#d2--repository-visibility)** — unaffected, and now known to be
  unaffected: namespace verification uses a *temporary* public repository holding a
  verification key, not a public project repository. The repository is currently private,
  which does not block publishing. D2 stays the owner's call on its own merits.

  **Corrected 2026-08-25:** the repository is no longer private — the owner made it public,
  settling D2 ([ADR-0034](../adr/0034-the-repository-is-public-before-it-is-released.md)).
  The finding above is unaffected in substance: publishing was never gated on visibility in
  either direction, which is precisely why the decision could be taken on its own merits.

---

### Task 0.8 — Watch strategy spike

**Status:** Partly done — behaviours 1–3 observed on Linux 2026-08-20 and written up;
**behaviour 4 (macOS latency) is not measurable on this machine** and remains open

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

#### Found

Run on **Linux 7.0.11**, `sun.nio.fs.LinuxWatchService` (inotify), Temurin **25.0.3**. The
harness is a single-file JDK program kept in the local-only `brainstorm/spikes/`; the method
is described below in enough detail to rebuild it, and the M3 regression tests are its
permanent form.

**1. Filename filtering — works.** `WatchEvent.context()` is the filename relative to the
watched directory, never a path, so filtering is a string comparison. Writing three files
into one watched directory produced events distinguishable by name with no ambiguity.

**2. Temp-file-then-rename — confirmed, and it surfaces as `ENTRY_CREATE`.** ADR-0013's
premise holds:

| Write pattern | Events observed |
|---|---|
| in-place rewrite | `ENTRY_MODIFY app.conf` — **1 or 2 events, varying between runs** |
| temp-file-then-rename | `ENTRY_CREATE app.conf.tmp`, `ENTRY_MODIFY app.conf.tmp`, `ENTRY_DELETE app.conf.tmp`, **`ENTRY_CREATE app.conf`** |

Two things follow. Treating CREATE and MODIFY identically is necessary, as ADR-0013 said. And
the *same* filename filter that ADR-0024 removes for symlinks is what discards the three
`.tmp` events here — it is load-bearing in the ordinary case.

**3. ConfigMap symlink swap — ADR-0013 was wrong twice.** Full three-level layout
(`..gen1/app.conf`, `..data` → `..gen1`, `app.conf` → `..data/app.conf`), swapped by staging
`..gen2`, creating `..data_tmp` and `ATOMIC_MOVE`-ing it over `..data`:

| Strategy | Events observed |
|---|---|
| Watch the **real path's** parent, resolved at registration (what ADR-0013 decided) | **none at all** |
| Watch the directory containing the **symlink** | `ENTRY_CREATE ..2026_08_20_gen2`, `ENTRY_CREATE ..data_tmp`, `ENTRY_DELETE ..data_tmp`, `ENTRY_CREATE ..data` |

**No event names `app.conf`.** So ADR-0013's "filter events by filename" would have discarded
all four and missed the swap even under the strategy that sees it. Both halves are corrected
by [ADR-0024](../adr/0024-watch-the-symlink-s-directory-not-its-real-path.md). After the swap
the visible path still resolved, its real path had moved to `..gen2`, and its content read as
generation 2 — so the change is detectable, and only the *trigger* was missing.

**4. Latency — measured here, but not on the platform the task asks about.** On Linux, over
20 samples of write → event observed. The machine is part of the finding: AMD Ryzen 7 7840HS,
ext4 on NVMe, Pop!_OS 24.04 (kernel 7.0.11), Temurin 25. A latency figure without the
hardware it came from is not reproducible and not worth quoting.

| min | median | max |
|---|---|---|
| 0.37 ms | **0.50 ms** | 0.63 ms |

**5. Debounce sizing (added to the spike).** Events for one logical write arrive in a burst
spread over **≤ 2.53 ms** (in-place: 1–2 events; rename: 4 events). ADR-0013's ~300 ms
debounce is roughly 100× the observed spread on Linux — comfortably safe, and the varying
event count per write confirms the debounce is required rather than merely tidy.

#### Still open — macOS

**The macOS figure cannot be produced from this machine, which is Linux.** The task asks for
it as a number and this entry does not have one, so the task stays *Partly done* rather than
being closed with the gap papered over. What is blocked by it:

- ADR-0013's macOS caveat is still un-measured, so the README's latency note can state the
  Linux figure only.
- [ADR-0002](../adr/0002-scope-to-langchain4j-llm-configuration.md)'s push-based claim is
  confirmed on Linux and unverified on macOS.

It needs a macOS machine or a CI runner. **M3 is not blocked** — the three behaviours that
determine the watcher's *design* are settled, and the macOS number qualifies documentation
rather than changing the implementation.

> **Incidental finding: `CLAUDE.md`'s toolchain note was out of date.** It recorded JDK 21;
> this machine runs Temurin **25.0.3**. Corrected in the same change. It does not affect
> [ADR-0019](../adr/0019-target-java-17.md) — the compile target stays `release` 17 and the
> CI matrix decision is unchanged — but "build on a newer JDK" now means 25 locally.
