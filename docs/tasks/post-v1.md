# Post-v1 work

Work taken on after [M5](milestones.md#m5--release-readiness) closed v1, which is neither a
verification task nor a milestone. Items are numbered `P1`, `P2`, … and, like every other
identifier here, are never renumbered.

Milestones describe what v1 had to contain. This file is for everything that comes after
without belonging to the one milestone that is still scheduled (M6, publishing).

---

### P1 — Console chat example

**Status:** Done 2026-08-23 · **Branch:** `task/p1-console-chat-example`

An interactive example: read the configuration layers, list every configured name in a
menu, let the user pick one, chat with it in the terminal, and return to the menu with a
command.

**Why it is worth having.** The library's headline behaviour — edit a file, and a running
application picks it up without a restart — is pinned by 17 reload tests and is still
completely abstract until you watch it happen. `ThreeModelCouncil` asks each model one
question and exits, so it never observes a reload. Nothing in the repository did.

#### Built

`ConsoleChat` in `modelrack4j-examples`. Commands are `/menu` to go back to the list and
`/exit` to leave.

It uses each optional part of the bundle when it is present and skips it when it is not:
streams when a `StreamingChatModel` was configured, moderates the question on the way in
when a `ModerationModel` was, and keeps conversation history when a `memory` block was —
printing *"no memory configured: each turn is independent"* when there is none, because that
is what that configuration actually means and it is better said than silently demonstrated.

It calls `registry.get(name)` **once per turn**, which is the habit
[ADR-0009](../adr/0009-holder-api-primary-listeners-optional.md) exists to encourage, and
the reason the reload is visible at all.

**Three behaviours were driven end to end with dummy keys**, so no money was spent and no
provider was contacted beyond the rejected authentication:

| Driven | Observed |
|---|---|
| menu → chat → `/menu` → menu → `/exit` | the loop, and the choice accepted by number or by name |
| a block **added** to the file mid-session | `[config reloaded: updated=[] added=[DEMO] removed=[]]`, and the next menu listed four models instead of three |
| the block **being chatted with** removed | `removed=[SH]`, then `[SH is no longer configured — back to the menu]`, and a menu of two |

The second and third are the interesting ones: they are
[ADR-0014](../adr/0014-lifecycle-of-removed-names-and-superseded-bundles.md)'s lifecycle
rules, seen rather than asserted.

#### Found — the example is the first code that could hit this

`chat()` looked up the bundle once on entry, to get the memory provider, and that call was
outside any `catch`. A name removed between the menu being drawn and the choice being made
would therefore terminate the application with an `UnknownConfigurationException` stack
trace, rather than returning to the menu the way the per-turn lookup does.

The window is small and the bug is real: a watched file can change at any moment, which is
the entire premise. It is fixed, and it is the sort of thing only an interactive consumer
finds — every test in the suite knows exactly when its own config changes. Worth remembering
the next time an example looks like decoration rather than coverage.

#### Not covered

The streaming, moderation and memory paths have **never run against a live provider** — the
same gap the integration tests still have. With dummy keys the request is rejected at
authentication, so what is proven is that the code takes the right branch and reports the
failure without ending the session.

---

### P2 — A short description per configuration

**Status:** Done 2026-08-23 · **Branch:** `task/p2-configuration-description`

An optional `description` key on each named block, and the examples updated to show it.

**Why.** Names are short because the application types them on every lookup, which leaves
`SL`, `SH` and `CR` meaningless to everyone who did not write the file — including the
console menu [P1](#p1--console-chat-example) had just added, where three initials were the
only thing on offer.

#### Built

`LlmConfig` gains `Optional<String> description`, read from `description` in the block. The
library never reads it: it exists for menus, admin screens and operators.

**Two edges, both pinned by tests:**

| Case | Behaviour |
|---|---|
| `description = ""` or `"   "` | rejected, and the message names `description = null` as the way to clear one |
| `description = null` in a higher layer | clears a description set lower down — HOCON's null removes the key, so `hasPath` is false |

The second is a claim the first one's error message *makes*, so it has its own test in
`LayeredResolutionTest` rather than being assumed to work.

**The design question was whether it belongs in the diff**, settled as
[ADR-0032](../adr/0032-description-is-part-of-the-config-record.md): it is an ordinary record
component, so editing prose alone rebuilds that one bundle. Excluding it would have meant
hand-writing `equals` on a record, breaking [ADR-0006](../adr/0006-named-configurations-with-per-name-diffing.md)'s
one-sentence rule, and — the part that decided it — leaving `config().description()`
returning superseded text forever, which is a stale read with no signal.

**Examples updated:** the console menu prints the description under each entry and in the
chat greeting, `ThreeModelCouncil` prints it under each header, and `council.conf` carries
one per block. In the README's quick start the descriptions replace the trailing `#` comments
that were doing the same job less usefully.

#### Found

Adding the description to the console's greeting reintroduced the exact bug
[P1](#p1--console-chat-example) had just fixed: a second `registry.get(name)` outside the
guarded lookup. Caught before commit, and fixed properly this time — one guarded lookup now
serves the whole greeting, with a comment saying why a second call does not belong there.

Twice in two days on the same three lines suggests the shape is the hazard, not the
attention: `get()` reads mutable shared state and looks exactly like reading a field.

---

### P3 — The manual

**Status:** Done 2026-08-23 · **Branch:** `task/p3-manual`

A developer and user manual in `docs/manual/`: [Part 1](../manual/part-1-tutorial.md), a
tutorial built on the two runnable examples, and [Part 2](../manual/part-2-reference.md), the
reference.

**Why it is separate from the README.** The README answers *what is this* in five minutes and
has to stay that length. Nothing answered *how do I actually use it*, and nothing at all
documented the schema completely — the key table in the README is a summary, and the Javadoc
is organised by type rather than by task.

#### Built

Part 1 runs from an empty directory to a console chat that hot-reloads, in ten steps:
install, first config, first conversation, adding a model **while it runs**, memory and
streaming, three deliberate validation failures, a deliberately broken file, layering, the
three-model council, and finally the dependency and ten lines of Java for the reader's own
project.

Part 2 is the reference: concepts, the complete key table, layering rules, memory and the
token-cost rule, the full API surface, exactly what a reload guarantees, the watcher's
behaviour and measured latency, the logging table, the provider matrix with per-provider
notes, the SPI, threading and lifecycle, scope boundaries, an eleven-row troubleshooting
table, and versioning.

**Every command and every output block in Part 1 was run before it was written.** The outputs
are captures, not reconstructions — including the three validation failures, whose exact
messages are quoted, and the reload sequence. Where output depends on a model's answer the
page says so rather than inventing a plausible one.

#### Found — two things, both from running the documented commands

1. **The examples' own `mvn exec:java` instruction was incomplete and would fail.**
   `exec:java` resolves `modelrack4j-core` from `~/.m2`, not from the reactor, so without a
   prior `mvn install` it runs against whatever stale jar is there — which surfaced as
   `NoSuchMethodError: LlmConfig.description()` from the version installed before
   [P2](#p2--a-short-description-per-configuration). The Javadoc on both examples now shows
   the `install` step, and the tutorial explains why, since the error message names nothing
   that would lead you there.
2. **`exec-maven-plugin` was unpinned.** Being invoked only from the command line, it was
   never declared, so plugin-prefix resolution took whatever was newest on Central — a
   different build on two machines and on two days. Pinned at `3.6.3` in the parent's
   `pluginManagement`.

Both are the kind of defect that only appears when someone runs the instructions instead of
reading them.

---

### P4 — Two examples for the two undemonstrated strengths

**Status:** Done 2026-08-24 · **Branch:** `task/p4-strength-examples`

The README claims four strengths. An audit found two of them had no runnable demonstration
at all: **the provider becoming configuration**, and **snapshot-wide reload atomicity**. The
second is the subtlest thing the library does and a reader simply had to take it on trust.

#### Built

**`AtomicSnapshot` — the guarantee made visible, for free.** `SL` and `SH` are both tagged
`gen-1` in their `description`. Four threads read the pair as fast as they can while a single
save changes **both** blocks to `gen-2`. Measured on this machine:

```
pairs observed, in the order they first appeared:
  1.  SL=gen-1  SH=gen-1                73,753,045 samples
  2.  SL=gen-2  SH=gen-2               169,238,048 samples

torn pairs (SL and SH from different generations): 0
```

243 million observations, no mixed pair. It reads configuration only — literal credentials,
no request ever sent — so it is **the only example that needs no API key and costs nothing**,
which makes it the one to hand someone who wants to see something work in thirty seconds.

**It was verified by being made to fail.** A demonstration that cannot fail proves nothing, so
`reload()` was temporarily sabotaged to publish `SL` five milliseconds before the rest of the
snapshot. The example immediately reported the torn pair it exists to catch:

```
  2.  SL=gen-2  SH=gen-1                    28,342 samples
torn pairs (SL and SH from different generations): 28342
TORN — this is a bug: the snapshot swap is no longer atomic.
```

The sabotage was reverted from a backup; `git diff` confirmed the tree clean afterwards.

**`ProviderSwap` — the headline claim, demonstrated.** Asks a question, rewrites the file from
`provider = anthropic` to `provider = openai`, waits for the reload, and asks the same
question through the same method. The output moves from `AnthropicChatModel` to
`OpenAiChatModel` with no recompile and no restart. `ask()` names no provider, imports no
provider type and has no branch — it cannot tell who answered, which is the point.

Without both keys it explains what it needs and exits rather than failing, and points at
`AtomicSnapshot` as the free alternative. A rejected key is caught and reported per call, so a
bad credential does not hide the swap the example exists to show.

#### Verified

`AtomicSnapshot` end to end, including its failure mode. `ProviderSwap` end to end **except
the two answers**: run with dummy credentials, both requests reached their provider and came
back rejected at authentication, which proves the whole path including the network — only a
valid-key response is unproven, the same gap the integration tests still have.

#### The manual, updated with them

Its index claimed "the two runnable examples" and there are now four — the kind of line that
goes stale silently. Replaced with a table of all four and what each costs to run, since the
free one is the useful thing to know.

Two real gaps closed at the same time. Part 1's step 4 now shows that the same edit which adds
a model also **changes its provider** — three lines and a save, pointing at `ProviderSwap` for
the unattended version. The tutorial had never demonstrated the library's headline claim.
Part 2 gained an `Examples` section: one row per program, what it demonstrates, and what it
needs, including the note that `AtomicSnapshot`'s zero is a measurement rather than a
decoration because sabotaging the swap makes it report tens of thousands.

#### Completeness pass on Part 2

Audited mechanically rather than by reading: every public API member (28 of 28) and every
configuration key the loader reads (14 of 14) is documented. One real hole —
`grep -cE "modelrack4j-bom|<dependency>|artifactId"` returned **0**, so a reference manual
never said what to put in a POM. Added a `Dependencies` section: the BOM import, one artifact
per provider, the rule that core knows no providers, core's actual dependency tree, and the
reminder that an SLF4J binding is required or every rejected-reload warning is discarded.

Two threading facts were also missing and are the kind asked once and needed answered:
listeners may be registered at any time from any thread (the lists are copy-on-write), and
registering one never replays a reload that already happened.

Part 1 gained four sentences of orientation. It opened with *"Forty minutes, start to
finish"*, which serves a reader arriving from the README and strands one arriving from a
shared link. It now states what the library is for and hands the argument back to the README's
`Why` rather than repeating it — purpose, not pitch. Part 2 deliberately gets none of this; a
reference opens with concepts.

> The orientation paragraph originally linked the README's `Should you use this?` as well.
> That anchor lives on a different unmerged branch, so the link checker caught a cross-branch
> dependency that would have dangled if the two merged out of order. Removed the second link
> instead of sequencing the merges: the README's `Why` already ends with a bridge to that
> section, so one link does the work and P4 stays independently mergeable.

#### Not done

The examples module still depends on OpenAI and Anthropic only, so nothing exercises Gemini or
GLM. Adding them is easy; making a *demo* out of them is not, because mandatory `${VAR}`
substitution is all-or-nothing per snapshot — a four-provider example file needs all four keys
or nothing loads. Not worth solving for a demonstration.

---

### P5 — Repository hygiene: ignore rules and a contributing guide

**Status:** Done 2026-08-24 · **Branch:** `docs/gitignore-and-contributing`

Two items from the pre-publication verification, both small and both only mattering once the
repository is visible ([D2](open-decisions.md#d2--repository-visibility)).

#### Built

**`.gitignore` now covers credential-bearing local files.** It previously listed only
`brainstorm/`, `target/`, IDE directories and `.DS_Store`. Added `local.conf`,
`*.local.conf`, `.env`, `.env.*`, `application-local.*` and `**/secrets.*` — deliberately
wider than the files this project happens to use, because the library's entire domain is
configuration files holding API keys.

Also added `.claude/settings.local.json`, which was previously ignored **only by the author's
global ignore file** (`~/.config/git/ignore`). That does not travel: on any other machine, or
a fresh clone, it would appear untracked and could be committed. The shared
`.claude/settings.json` is deliberately left committable.

**The rules were tested rather than assumed, and one gap was found that way.** `git check-ignore`
against twelve representative paths showed `*.local.conf` does **not** match a file named
plainly `local.conf` — which is exactly the name [Part 1 step 8](../manual/part-1-tutorial.md)
of the manual tells the reader to create for their developer override. Added as its own
entry. Reading the pattern would not have caught it.

**`CONTRIBUTING.md`, deliberately short.** Its job is defensive: to make "open an issue first"
the obvious path, so an unsolicited large pull request that does not fit the scope never has
to be declined after the work is done. It points at the README's out-of-scope list and at
`docs/adr/` for the "would you take a PR for X?" questions those already answer, states that
the default build must pass offline with no keys, and repeats the house rule that a test which
cannot fail is worse than no test.

---

### P6 — The integration tests against live APIs

**Status:** Done 2026-08-24 · **Branch:** `fix/it-model-ids`

`mvn -Pintegration verify` had never reached a provider. Both of its guards were verified in
[M4](milestones.md#m4--gemini-and-glm) — the profile, and the per-class key check — but the
payload behind them had never executed, so every claim about the four provider factories
rested on unit tests against fakes. This was the last thing in the project resting on code
that nothing had ever run.

#### Result

All four pass, in one run of the documented command:

```
mvn -Pintegration verify                                     BUILD SUCCESS

openai     gpt-5-mini          Tests run: 1, ... Skipped: 0    3.272 s
anthropic  claude-sonnet-4-6   Tests run: 1, ... Skipped: 0    1.695 s
gemini     gemini-3.6-flash    Tests run: 1, ... Skipped: 0    3.225 s
glm        glm-5.3             Tests run: 1, ... Skipped: 0    2.441 s
```

`Skipped: 0` on every row is the part that matters: it distinguishes *ran and passed* from
*silently skipped because a key was absent*, which is the failure mode the second guard makes
possible and which looks identical in a build summary.

#### Changed

Two model IDs in integration tests, one line each. Nothing in `src/main` changed; no
user-facing string ever named either model.

- `gemini-2.5-flash` → `gemini-3.6-flash`
- `glm-4.6` → `glm-5.3`

#### What the failures taught, which is most of the value

Three of the four failed before they passed, and **every failure landed past everything this
library is responsible for.** In each case the config parsed, `${VAR}` resolved, validation
passed, the `ServiceLoader` found the factory, the factory built a real client, and the
provider authenticated the credential — then refused for its own reasons.

1. **OpenAI — `insufficient_quota`.** An unfunded account. Diagnostically this was the most
   useful failure of the three: it is a *post-authentication* error, so it proved mandatory
   substitution had carried a real key all the way to an authenticated HTTP call. An
   unresolved `${OPENAI_API_KEY}` throws `ConfigException.UnresolvedSubstitution` and never
   reaches the network at all.

2. **Gemini — `404`, model retired.** *"This model models/gemini-2.5-flash is no longer
   available to new users."* [M4 predicted exactly this](milestones.md#m4--gemini-and-glm):
   `langchain4j-google-ai-gemini` ships no model-name enum, so that string was recorded as the
   one in the milestone that only a live call could verify. It was, and it had rotted. The
   three providers whose IDs came from an upstream enum were the three that did not.

3. **GLM — `余额不足或无可用资源包，请充值。`** Read as an unfunded account, and that reading
   was wrong: the same key succeeded immediately on `glm-5.3`. The resource package covers
   5.3 and not 4.6, so the balance error was **model-scoped, not account-scoped** — a
   distinction the message does not draw and which cost one wrong diagnosis.

#### Findings recorded elsewhere

**Exception types are not portable across providers → [ADR-0033](../adr/0033-provider-exceptions-pass-through-untranslated.md).**
OpenAI's out-of-credit arrives as `RateLimitException`; GLM's arrives as `ZhipuAiException`.
The same real-world condition, two types, decided by which provider the configuration names.
The ADR settles that they pass through untranslated and fixes the swap guarantee at its true
width — construction, not invocation — and the README and manual now say so.

**`glm-5.3` is not in the upstream enum.** `ChatCompletionModel` in
`langchain4j-community-zhipu-ai-1.19.0-beta29` has `GLM_5_1`, `GLM_5`, `GLM_4_7`, `GLM_4_6`
and no `GLM_5_3`. `GlmProviderFactory` passes `config.modelName()` through as a raw `String`,
so this works — but GLM's model name is no longer enum-checked, and it joins Gemini's as a
string only a live call can verify. M4's note is corrected in place.

#### Left alone deliberately

`GeminiProviderFactoryTest` and `GlmProviderFactoryTest` still name the old IDs. They never
touch the network, and beside `api-key = "k"` the string is plainly a stand-in rather than
exemplary configuration. Changing them would be churn in tests whose point is that any string
passes through.

#### Still open

**The ITs run four providers but assert only `isNotBlank()`** — correct, since model output is
not deterministic, but it means these tests prove *reachability*, not correctness of anything
the model said.

**`claude-sonnet-5` and `gpt-5.1`** — the IDs the README tells readers to copy — are still
exercised only by `ProviderSwap`, not by any IT. The ITs deliberately name cheaper models, so
running them does not cover the advertised ones.

**A model ID can rot at any time**, and now two of the four are outside upstream's enums. That
is a standing maintenance cost of the integration suite, not a defect: it is the suite doing
its job.
