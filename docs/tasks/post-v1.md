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

---

### P7 — Closing out the outside review of the public repository

**Status:** Done 2026-08-25 ·
**Produced:** [ADR-0035](../adr/0035-ship-a-notice-file-for-attribution.md),
[ADR-0036](../adr/0036-claude-md-is-local-only.md)

The repository went public on 2026-08-25 ([D2](open-decisions.md#d2--repository-visibility),
[ADR-0034](../adr/0034-the-repository-is-public-before-it-is-released.md)). An outside review
followed, reading only what a stranger could see: `README.md`, the ADR index, and the manual
index. Ten findings, investigated one at a time against the actual files.

#### What the investigation found

Two findings did not survive contact with the code, and the more interesting one failed in
the project's favour.

**Core is not accumulating dependencies.** The review reasoned from the ADR index that core
had taken on the `langchain4j` aggregate, `com.typesafe:config` and `slf4j-api`, and that the
cumulative effect worked against the original "`langchain4j-core` only" goal.
`mvn dependency:tree` says otherwise: core's whole compile scope is **six artifacts**, and
the aggregate contributes exactly one jar with **no new transitive dependency at all**. The
number is now recorded in [M0's verification block](milestones.md#m0--skeleton-and-ci).

**Nothing contradicted anything on GLM token estimation.** The review read ADR-0021's title
("token estimation is universal") against the README's provider table (GLM: none) and
concluded one must be wrong. Both are right. `GlmProviderFactory` returns `ABSENT`, the README
matches it, and ADR-0021's body is correct *for its date* — it surveyed four modules before
the GLM route was settled and deliberately kept `ABSENT` representable against exactly this
outcome. What was missing was one status line: ADR-0021 handed amendment pointers to ADR-0004
and ADR-0010, then never received its own when ADR-0022 falsified its premise the same way.

**Every model identifier a reader is told to copy still resolves.** Re-checked live against
all three provider APIs on 2026-08-25. The retired strings survive only in unit tests, which
never make a call, and in dated records here.

**The Spring Boot / Quarkus comparison still holds.** Both upstream pages re-fetched the same
day and scanned for `reload`, `refresh`, `restart` and `hot`: zero occurrences on either. One
objection to expect anyway — a Quarkus user may point at dev-mode live reload, which is a
Quarkus feature rather than a documented capability of the LangChain4j starter.

#### The two decisions it forced

**[ADR-0035](../adr/0035-ship-a-notice-file-for-attribution.md) — a `NOTICE` file after all.**
[ADR-0017](../adr/0017-apache-2-0-license.md) had ruled one out, and it had genuinely weighed
the question — but only as *"does a NOTICE carry attribution someone else is owed?"*, the
vendored-code case. It never asked whether a NOTICE makes a **fork credit this project**,
which is a goal the owner holds and which has a different answer: §4(c) protects source
headers only in the Source form, so **§4(d) is the only clause in the licence that follows the
work into a binary** — and it is conditional on a NOTICE existing. `LICENSE` and `NOTICE` now
ship inside `META-INF/` of every jar, verified by reading them back out of the built
artifacts.

**`CLAUDE.md` — untracked, then reversed within hours.**
[ADR-0036](../adr/0036-claude-md-is-local-only.md) removed it from the tree, arguing that it
summarises `docs/` and can drift against it.
[ADR-0037](../adr/0037-claude-md-is-tracked-and-maintained.md) supersedes it and puts the
file back. Both steps are recorded because the reason the first was wrong is the useful part.

Two things surfaced after ADR-0036 and together they reverse it.

**Untracking removes nothing.** `CLAUDE.md` is in the first commit and in the head commit of
all 26 merged pull requests, which GitHub serves at `refs/pull/N/head` permanently — a
force-push does not touch them. Verified by fetching PR #4's head and reading the file out of
it, 11,222 bytes. Rewriting `main` would change 28 SHAs and leave it exactly as reachable;
the only complete removal is deleting and recreating the repository, which destroys the pull
requests and their review history. So the privacy question was never live.

**The file was materially false in four claims in its opening section** — "no code exists
yet" against 27 `src/main` Java files, "no POM and no source tree" against seven modules, "no
remote configured" against 26 merged PRs, and "ADR-0002 … ADR-0014" against 37 ADRs. Worse,
its watcher guidance still said *"resolve symlinks to their real path"*, which
[ADR-0024](../adr/0024-watch-the-symlink-s-directory-not-its-real-path.md) reversed after the
Task 0.8 spike. A session following it would have reintroduced a fixed bug.

**Which inverts ADR-0036's argument.** Drift is a reason to track, not to hide: a tracked
file changes through pull requests and gets read, an untracked one drifts with no reviewer
and no diff. `CLAUDE.md` reached four false statements *while nominally tracked* because
nobody treated it as a document with an audience — untracking would have removed the only
mechanism that could catch that and left the false version as the last public copy. The file
is now current, and a stale instruction in it counts as a defect.

#### Left alone deliberately

**The tutorial was not reordered.** The review suggested restructuring so the free part comes
first. It already did — steps 1 and 2 send no request. What was missing was the label, and the
distinction that was actually doing the discouraging: every step needs an API key to be *set*,
because substitution is mandatory, but only four need it to be *funded*. Reordering would break
every anchor and the "every command was run before it was written down" guarantee, to buy
something labelling already buys.

**The cost is quantified in requests, not money.** About a dozen short prompts, three of them
in step 9. Dollar figures depend on pricing pages that move and that nothing here can
re-verify; a request count is checkable from the page itself.

#### Still open

**`CLAUDE.md` now carries a standing obligation.** Every change that invalidates a line in it
must fix that line in the same commit. That is the cost ADR-0036 was trying to avoid paying,
and it is cheaper than guidance that quietly instructs the next session to undo finished work.

**ADR-0036's body still describes a dead link in ADR-0025** that is alive again, its target
being tracked once more. That body is frozen and stays as written;
[ADR-0037](../adr/0037-claude-md-is-tracked-and-maintained.md) is where a reader learns it was
reversed. The supersede mechanism working, not a defect in it.

**The freeze itself was sharpened during this work.** A measurement was appended to ADR-0020's
body and the owner ruled it out. `CLAUDE.md` and [`../adr/README.md`](../adr/README.md) now
say frozen means frozen against *additions*, however clearly dated, with `Status`,
`Supersedes` and `Amends` named as the only mutable lines.

**One stale phrase, noted in place rather than rewritten:** M0's verification row says core
resolves to `langchain4j-core`, the aggregate, `com.typesafe:config` "and nothing else". True
at M0; `slf4j-api` became a declared dependency at M1 under
[ADR-0028](../adr/0028-core-logs-through-slf4j-api.md).

---

### P8 — Status-line drift, and a check that would have caught it

**Status:** Done 2026-08-26 · **Branch:** `docs/p8-status-drift-and-doc-checks`

An audit of `CLAUDE.md` after it was brought back under version control
([ADR-0037](../adr/0037-claude-md-is-tracked-and-maintained.md)) found three defects in it —
and finding them exposed a larger one in the ADR index that had been there for months.

#### The one that matters: four ADRs never got the pointer their index row records

The supersede/amend mechanism is a status line. [ADR-0013](../adr/0013-watch-directories-resolve-symlinks.md),
[ADR-0018](../adr/0018-manage-langchain4j-versions-via-bom.md),
[ADR-0019](../adr/0019-target-java-17.md) and
[ADR-0020](../adr/0020-core-depends-on-langchain4j-aggregate.md) each read plain `Accepted`
in their own header while the index row beside them recorded an amendment. A reader opening
the ADR — the normal way to read one — saw a decision that looked untouched.

**It survived two hand-rolled checks**, both of which reported "0 mismatches". Both compared
only the text *before* the em-dash, so `Accepted` and `Accepted — dependency set amended by
ADR-0028` looked identical. The check was structurally incapable of finding the thing it was
written to find, and said so in a reassuring green number.

Fixed in the ADR headers rather than the index rows: the ADR's own header is authoritative.
Two more rows (0017, 0021) had drifted by an article and were aligned to their files.

#### `CLAUDE.md`

- "**Three** documents matter" above **four** bullets — introduced days earlier when
  `docs/manual/` was added to the list.
- **Core's dependency list omitted `slf4j-api`**, and its amendment chain stopped at
  ADR-0020 without reaching [ADR-0028](../adr/0028-core-logs-through-slf4j-api.md). The POM
  declares four compile dependencies; the file named three. This is precisely the failure
  the same file warns about eighteen lines above it — *"where a summary and an ADR disagree
  the ADR wins and the summary is the bug"*.
- "Milestones run **M0 → M6**" against a `milestones.md` with headings for M0–M5 only. M6 is
  named in prose and deliberately has no entry, being unscheduled. Now says so.
- `Task 0.4` was offered as an example of an ID cited from that file; after the rewrite it
  is not cited there at all. Changed to `Task 0.8`, which is.

#### One arithmetic error, in two places

`docs/tasks/README.md` said "three of the **seven** verification tasks refuted the premise
they were written with", and `CLAUDE.md` had copied it. There are **eight** — and one of the
three refutations is Task 0.8 itself, so the sentence excluded a task it was counting.

#### `build/check-docs.py`

Every check in it exists because the corresponding mistake was actually made. It compares
**whole** status strings, and it resolves every link and anchor against `git ls-files` rather
than the filesystem — a target that is git-ignored resolves fine locally and 404s for
everyone who clones, which is how a broken link passed clean the day `CLAUDE.md` was
untracked.

**Proven by breaking the repository on purpose**, not by passing on a clean one: reinstating
ADR-0020's drift, adding a link to a git-ignored file, and adding a bad anchor each produce
exit 1 with the offending line named. Wired into CI as a third job alongside the JDK matrix
and the offline build.

#### What a second pass found, after the first fix was already committed

Re-checking by hand rather than by re-running the new script — the script being the thing
under suspicion — turned up two more, one of them freshly introduced by this very task.

**A status line written during this task did not follow the documented shape.** ADR-0018 was
given `Accepted — one import becomes two, per ADR-0022`, copied verbatim from its index row.
That records the amendment in prose a reader scanning for "amended by" would slide straight
past — the same legibility failure the task was opened to fix, reintroduced while fixing it.
Now `Accepted — the BOM import set amended by ADR-0022`.

**One legitimate variant was not documented.** ADR-0008 has read *"swap scope widened by
ADR-0012"* since the first commit. That is not a deviation — the folder README defines an
amendment as one that "narrows or widens" — but the Status values table only ever showed
`amended`. The table now records `widened` and `narrowed` as equally valid, so the shape can
be enforced without flagging a line that was right all along.

**Amendments are reciprocal, and nothing was checking it.** If A's status says it was amended
by B, B's `Amends` header must name A, and the reverse. Both directions were verified clean
across all 37 ADRs — but only by an ad-hoc script, which is exactly how the original drift
went unnoticed for months. Both directions are now checked on every run.

#### One thing this task got wrong

**A `.pyc` was committed.** `python3 -m py_compile build/check-docs.py`, run while verifying
the checker, left a `build/__pycache__/` that `git add -A` swept into the branch. It reached
`main` in #28 and was removed immediately afterwards, with `__pycache__/` and `*.py[cod]`
added to `.gitignore` — rules that did not exist because until this task the repository had
no Python in it.

Worth recording rather than quietly deleting: five CI jobs, a purpose-built documentation
checker and a hand audit all passed, and none of them was looking at what the commit
*contained*. Every check added here reads the documentation; nothing checks the shape of the
tree itself.

#### Still open

**M6 has no entry in `milestones.md`.** Deliberate — it is unscheduled, so there is nothing
to record beyond its trigger — but it means a reader following a pointer to "M6" finds prose
rather than a work item. Worth writing when publishing is actually scheduled, not before.

---

### P9 — The three things that had to be right before a first release

**Status:** Done 2026-08-26 · **Branch:** `task/p9-release-blocking-fixes` ·
**Produced:** [ADR-0038](../adr/0038-snapshot-gives-callers-the-atomicity-the-swap-already-has.md)

An outside list of pre-publication items. Four of its points were checked against the code;
one was already done, one was the most serious defect found in the project so far.

#### The atomicity guarantee was one notch wider than the truth

[ADR-0012](../adr/0012-reload-atomicity-is-snapshot-wide.md) makes the *swap* atomic, and it
is. What nobody had asked is how much of that a caller receives. `LlmRegistry.get(String)`
reads the published generation on every call — that is what makes reload visible — so two
consecutive calls are two independent reads, and a swap landing between them returns bundles
from different generations.

**Measured rather than argued.** A harness driving ~400 reloads at a 1 ms debounce, with one
thread reading the pair as fast as it could, produced **220 mixed pairs in 111,597,529
reads** — about two per million. Rare enough never to show up in a normal run of
`AtomicSnapshot`, which is exactly why the example reported zero and the README claimed *"the
mixed pair never appears"*.

Fixed by giving callers a handle on the generation rather than by narrowing the claim:
`registry.snapshot()` performs one read and returns an `LlmSnapshot` from which every lookup
belongs to that generation. `get()` is unchanged and stays primary; its Javadoc now states
what it does not promise. Four tests in `ReloadTest` pin the behaviour, deterministically —
a held snapshot keeps the old generation across a reload, its names agree, it keeps a name a
reload removed, and it rejects an unknown one.

`AtomicSnapshot` now samples **both** ways and prints both columns, so the boundary is
demonstrated instead of asserted. Core went from 59 to 63 tests.

#### `Automatic-Module-Name`, and the collision it caused

Absent from all five published jars. It becomes API on publication — a consumer with a
`module-info` requires it by name — so it had to be set before a first release, never after.
Now `io.github.maxtrezzi.modelrack4j` for core and `…provider.{openai,anthropic,gemini,glm}`
for the providers.

**The first attempt broke the build**, informatively: `modelrack4j-examples` inherited the
parent's default and ended up advertising *core's* module name. Two automatic modules cannot
share a name, and javadoc stopped resolving core's types entirely — an error that reads as
"cannot find symbol" and says nothing about module names. Examples now has its own.

#### Reproducible builds

`<project.build.outputTimestamp>` was absent, so every build embedded "now" and no consumer
could verify that a published jar came from the tag it claims. Set, and **verified by
building twice and comparing SHA-256** — byte-identical. Bump it at each release.

#### What the list got wrong

It said the integration tests had never reached a live API and called that the most
underrated risk. They ran on 2026-08-24 ([P6](#p6--the-integration-tests-against-live-apis)),
all four providers, and cost two stale model IDs and one portability ADR. The claim came from
reading M5, which said exactly that when it was true, without reading P6, which records that
it stopped being true. It also asked for the `io.github.maxtrezzi` namespace to be verified —
[Task 0.7](phase-0-verification.md#task-07--name-and-coordinates) established that Sonatype
grants it automatically — and for `maven.deploy.skip` on the examples module, which has had
it since M0.

#### Still open

Everything in the release mechanics proper: GPG key, Central Portal token, the `release`
profile with the signing and publishing plugins, dropping `-SNAPSHOT`, the tag, and an ADR
amending [ADR-0034](../adr/0034-the-repository-is-public-before-it-is-released.md), which
currently forbids one.

---

### P10 — A code review of `LlmSnapshot`, and a self-correction found while checking the fix

**Status:** Done 2026-08-26 · **Branch:** `task/p10-snapshot-review-fixes`

A review of the code P9 added — `LlmSnapshot`, the `LlmRegistry.get()`/`snapshot()` change,
the four new `ReloadTest` tests, and the updated `AtomicSnapshot` example. Four findings, all
in `LlmSnapshot.java`, none in the public contract.

**Duplicated logic.** `LlmRegistry.get()`/`names()` and `LlmSnapshot.get()`/`names()` were
byte-identical: the same lookup-or-throw and the same `keySet()` wrap, written twice. Fixed
by delegation — `LlmRegistry.get(name)` is now `snapshot().get(name)`, and `names()` likewise
— which costs nothing new (`snapshot()` already performs the single volatile read `get()`
always did) and leaves the lookup living in exactly one place.

**Three smaller ones**, all in `LlmSnapshot`'s constructor and Javadoc: the package-private
constructor took no `Objects.requireNonNull`, unlike every other method in the same class;
`names()` didn't say its result is sorted, though it inherits that from the same `TreeMap`
`LlmRegistry.names()` documents explicitly; and the constructor trusted the caller never to
mutate the map it was handed, an invariant that lives in `SnapshotLoader`, a different file.

**The documentation check this task was also asked to do caught a mistake in its own fix
— which then turned out to be a mistake in a different direction.** First pass: wrapping the
incoming map in `Collections.unmodifiableMap(...)` looked like the obvious defensive move,
until it was checked against the README's and the manual's near-identical claim that
`get()` is *"a read of a volatile field and a map lookup; cheap enough to call per request"*
— once `get()` routes through `snapshot()` on every call, an allocation in that constructor
lands on every lookup, which would have quietly falsified both documents. So the wrap was
dropped.

**That was also wrong, caught on a second review pass minutes later.** `Collections.
unmodifiableMap` recognises a map that is already one of its own unmodifiable wrappers and
returns that exact instance rather than allocating a new one — verified empirically with a
five-line program before trusting it, not assumed from memory, per this project's own Task
0.7 discipline. `SnapshotLoader` always hands out such a map, so wrapping it again in
`LlmSnapshot`'s constructor costs one `instanceof` check and nothing else. The wrap went back
in. Net effect on the constructor across both passes: still guards the invariant locally
(the map cannot be mutated regardless of what `SnapshotLoader` does in the future), and it
does so for the same zero allocations the no-wrap version had — the working code from the
first pass and the safety of the version before it, at once. The `requireNonNull` stayed
throughout.

Two lessons this leaves behind, not one. `build/check-docs.py` verifies links and ADR
metadata, not semantic claims about behaviour — the first catch came from re-reading two
sentences in the README and the manual against the new code, not from a checker. And a
performance argument is a claim about the JDK, which means it needs the same verification
discipline as a claim about an upstream library: the fix that "obviously" cost an allocation
did not, and the only way to know that was to write the five lines and run them.

Verified: core 63/63, reactor 96/96 — unchanged from before any of this, so two rounds of
tightening cost nothing. `javac -Xlint:all` clean on every pass. `build/check-docs.py` clean
across 38 ADRs and 52 files. Every remaining reference to `get()`/`snapshot()`'s internals in
`README.md`, `docs/manual/part-2-reference.md`, the CHANGELOG and ADR-0038 re-read against
the final code and found to still hold. A separate five-line program at the end confirmed,
running against the built jar rather than reasoned about, that two `snapshot()` calls in the
same generation print the same bundle set and that `get()` still resolves through the
delegation chain.

---

### P11 — The user-facing text, read as a non-native reader would read it

**Status:** Done 2026-08-27 · **Branch:** `docs/plain-english-pass`

Prompted by the owner failing to parse one of this repository's own sentences. The README
described the `AtomicSnapshot` example as *"One save changes two models; four threads sample
the pair both ways and count the mixed ones — via `snapshot()` the count is zero by
construction"*, which is accurate, short, and unreadable unless you already hold the mental
model it describes. The manual's version of the same sentence had the same problem.

**The distinction this settled, which is now the rule for this repository's prose.** Being
brief is not the defect; being brief *by way of a figure of speech the reader must unpack* is.
A short sentence is fine as long as it explains itself on a first reading. And the audience
is technical but does not read English as a first language, so idiom and rare vocabulary are
a second, independent way a sentence can fail — separate from whether the reader knows the
mechanism. Both tests now apply to the README, `docs/manual/`, public Javadoc, the commented
`.conf` files, CONTRIBUTING and the CHANGELOG. The deliberately terse register of the ADRs
and `CLAUDE.md` is unaffected: different audience, and an explicit choice recorded in
ADR-0001 and ADR-0015.

**What a full pass over that text found**, `docs/adr/` excluded because accepted ADR bodies
are frozen and `docs/tasks/` excluded as an internal record:

- **Two stale claims, not style at all.** The README announced "Two provider notes" above a
  list of three. `ThreeModelCouncil`'s Javadoc said the holder API "will make hot reload work
  when it arrives" — hot reload arrived in M3 and shipped in v1, so the sentence had been
  wrong since the day the feature landed and nobody re-read it.
- **Ten metaphors** that decode only for a reader who already knows the answer: a guarantee
  with a "precise width", a boolean that would "bless" every configuration, the caching trap
  "in a new costume", a decision "welded to" the build, memory built with "no ceremony", a
  module on the community "release train", symlink handling that is "not a tidy-up
  candidate", a "shorter fuse" for a "flaky" connection, seeing a guarantee "for nothing"
  (free, or pointless?), and capabilities that vary "depending on who you ask".
- **Nine vocabulary items above B2**, the largest being "straddle a reload" in six places
  across the README, two Javadoc classes and the CHANGELOG. The replacement was not invented:
  *"a reload landing between them"* was already the phrasing used elsewhere in the same files,
  so the fix was to stop maintaining two ways of saying one thing. Also "hearsay", "inert",
  "page someone", "collectable", "three days in", "sized against", "in miniature", "bites you".
- **Six sentences where the grammar was the obstacle rather than the words** — most notably
  the imperative-as-conditional *"Resolve each file as you parse it … and this throws"*, which
  appeared in both the README and the tutorial, and the dangling *"An unknown value lists the
  ones that are"*, which never says what the ones in question are.

**Two smaller findings the owner ruled on separately.** `licence` appeared once against
`License` everywhere else and in the filename, now uniform. And the Java sample in *Why* uses
`claude-sonnet-4-6` while every configuration example uses `claude-sonnet-5` — deliberate,
because that sample's whole point is a `temperature` fixed in a builder call and
`claude-sonnet-5` rejects a non-default temperature with a 400 (found by P6's live run). It
read as an inconsistency, so the reason is now stated where `temperature` is already
discussed.

**One thing this task got wrong about itself, worth recording.** The sentence added to
explain the `claude-sonnet-4-6` choice was first written as *"a temperature welded into a
builder call"* — reintroducing, three edits later, the exact metaphor this task had removed
from the paragraph fifty lines above. Caught immediately, but it says something about the
failure mode: the register is a habit, not a decision made once, and a pass like this does
not inoculate the next paragraph written.

Verified: `mvn clean install` and the full reactor suite green, offline; every edit is prose,
a comment, or one printed line, so no behaviour changed. `build/check-docs.py` clean. The
counts above were produced by grepping the whole user-facing set for each flagged phrase
rather than from reading impressions, which is what turned "straddle" from one sighting into
six.
