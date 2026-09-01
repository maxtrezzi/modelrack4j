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

**Corrected 2026-08-26 by [P9](#p9--the-three-things-that-had-to-be-right-before-a-first-release),
pointer added by [P14](#p14--a-coherence-pass-over-the-tracked-documentation).** No mixed pair
observed is not the same as no mixed pair possible. P9 measured about two per million pairs of
`get()` calls under a much higher reload rate; this run reported zero because one save gives
the window a single chance to open. The example now samples both ways and prints both
columns.

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

#### An outside review, and the regression it caught

`/code-review` over the whole branch, by a reader with no memory of writing it. Seven
findings; the first is the most serious defect this item produced.

**`parseString` silently disabled HOCON `include`.** `include "sibling.conf"` resolves
relative to the file that contains the line, and only `ConfigFactory.parseFile` knows which
file that is. Reading a file's bytes and parsing them as text moves the includer to the
classpath — and because an include is **allow-missing by default, nothing throws**: the
included block simply disappears. An existing user whose `llm.conf` includes `models.conf`
would have upgraded into a registry quietly missing those names.

Measured rather than argued, and the first attempt at measuring it was wrong: the experiment
put the configuration directory on the classpath, where `parseString` found the include after
all and the result came out backwards. Run again with the directory off the classpath,
`parseFile` resolves the include and `parseString` does not.

`ConfigLoader` now parses a file source with `parseFile` and everything else with
`parseString`. The `instanceof` that chooses is deliberate and is now in ADR-0042, in
`CLAUDE.md` and in the manual: an include is a directive about a *directory*, and a layer with
no directory of its own has no answer to give. There is a regression test, checked to fail
without the fix.

**Four smaller ones, all real.** `close()` promised that no listener runs after it returns,
which `FileChangeNotifier` only best-efforts — its join times out after five seconds — and
which a third-party notifier never promised at all; the Javadoc now says what is actually
guaranteed. A user-supplied notifier was dropped unclosed when a *layer* was invalid, because
`chooseNotifier()` runs before the load; the same ownership rule now covers that path.
`FileConfigSource.id()` was the raw path, so `a.conf` and `./a.conf` passed as two layers
while the same file listed twice was rejected; the id is now absolute and normalised.
`CLAUDE.md` still told the next session to use `ConfigFactory.parseFile(...)` *only* — the
instruction ADR-0042 had just reversed — and said nothing about `ConfigSource` or the reload
lock, so a future session would have undone both. Fixed in this commit, which is what
`CLAUDE.md` asks for.

**Two in this entry's own write-up.** The mutation-testing line said "147 mutants, 146 killed"
and then described a timeout, which leaves 148 out of 147 — the P14 pattern, and self-refuting
from its own numbers. It also read "Two further / further mutant timed out", a botched edit
that **a grep for `further further` does not find, because the duplication spans a line
break**. That is the "read the diff, do not grep it" rule turned on the person applying it.

**One the review got half right.** It reported that `origin().filename()` is `null` because of
`parseString`. `filename()` is indeed `null`, and the cause is `setOriginDescription`, which
replaces it — measured on both routes. The finding stands, the explanation did not.

**And one consequence the mutation run reported immediately.** With the loader parsing files
through `parseFile`, `FileConfigSource.text()` stopped being called by the library at all and
came back `NO_COVERAGE` on both its lines. It is still public behaviour through the interface,
so it now has its own tests — the UTF-8 read, the re-read, and the message for a file that is
not there.

#### A fifth example, because the two new features had none

Every v1 feature has a runnable example; P19's two did not, which is the gap
[P18](#p18--the-distance-between-arriving-and-running-something) exists to close.
`DatabaseSource` holds its configuration in memory instead of in a file, standing in for a
row, and calls `reload()` itself. It shows all four answers `reload()` gives — a name added, a
name updated, nothing changed, and a rejected reload after which the previous configuration is
still live — and it needs no key and sends no request, like `AtomicSnapshot`. `run-database.sh`
joins the four launchers, and `docs/manual/part-1-tutorial.md` gained the section the reference
already had: the tutorial is where reload is *taught*, and it only knew about files.

The first version of the example was wrong in a way only running it showed. Step 2 announced
"the user edits SL" and edited both names, because it rewrote the text with a string replace
that matched the identical `SH` block as well. It now builds each block from a name and a
model name, and prints `updated=[SL]`.

#### An accepted ADR was edited, deliberately

ADR-0042 never mentioned includes, and the fix above is exactly the kind of consequence its
*Consequences* section is for. The body of an accepted ADR is frozen, so this needs saying
plainly: **it was edited anyway**, because it has never been on `main`, is referenced from
nowhere outside this branch, and is part of the same unmerged change as the code it describes
— the same reasoning `CLAUDE.md` applies to renumbering an ADR before it is pushed. Once this
merges, the freeze applies normally and a further correction goes in a new ADR.

#### What the manual was still missing, found by enumerating the API rather than reading it

The reference promises *"every configuration key, every public method"*. Asked whether the
manual needed more, the answer came from `javap` over the built jar rather than from reading:
**17 public types, 67 public methods, and 2 undocumented.** Methods, not members: the count
excludes the 9 public constructors, and counting those too gives 76. The rule matters more
than the number, because without it the figure cannot be re-run — a later check read
"members" literally, got 76, and started writing a correction to a figure that was right.
To reproduce it: unpack the built core jar, run `javap` over every class in it, keep the
members of the types `javap` prints as `public`, and drop `equals`, `hashCode`, `toString`
and the enum's `values` and `valueOf`.

Four gaps, three of them introduced by this item:

- **`FileChangeNotifier` appeared nowhere.** A public class with three public methods, added by
  this item, named in no document. It now has its own table under *Asking for a reload*, and
  *The watcher* opens by saying that the watcher **is** that class — one implementation of
  `ChangeNotifier`, not something intrinsic to the registry.
- **The *Concepts* table defined a layer as "One configuration file".** The first definition a
  reader meets, contradicting the rest of the page. A layer is now text given as a
  `ConfigSource`, and *Notifier* joins the table beside it.
- **Three new failure modes had no troubleshooting row**: `watch(true)` refused for layers that
  are not files, two layers sharing an id, and an `include` in a non-file layer that adds
  nothing and says nothing. The last is the one a reader would never diagnose alone, because
  HOCON treats a missing include as success.
- **`UnknownConfigurationException.configurationName()`**, open since
  [P16](#p16--a-third-coherence-pass-and-the-surface-the-first-two-searched-past), is now named
  in the row that already sends readers into that `catch`.

**The two that remain undocumented are P16's, and stay open on purpose.**
`LlmConfig.fromBlock` and `MemoryConfig.unknownType` are accidental API surface, and P16's
reasoning holds: documenting them would make the accident permanent, and narrowing them is an
API change wanting its own item. One new fact for whoever takes it: **`unknownType` cannot
simply be made package-private**, because `MemoryConfig` is an `interface` and a `static`
method in an interface is implicitly public. Narrowing it means moving it out of the
interface. This also settles a claim made while reviewing P16 and reported as a miscount —
that `unknownType` was already package-private and P16 had counted three where there were two.
**P16 was right and that claim was wrong**: `javap` prints `public static`.

This audit is true on **2026-08-31**, and stops being true the next time the API grows. It
was re-run unchanged after the fourth review pass, which added no public member.

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

Audited mechanically rather than by reading, **on 2026-08-24**: every public API member
(28 of 28) and every configuration key the loader reads (14 of 14) was documented that day.
A completeness audit is true only on the date it runs, and this one stopped being true two
days later — [P9](#p9--the-three-things-that-had-to-be-right-before-a-first-release) added
`snapshot()` and `LlmSnapshot`, and
[P14](#p14--a-coherence-pass-over-the-tracked-documentation) found the reference had never
taken them. The date is added by
[P15](#p15--a-second-coherence-pass-and-what-the-first-one-missed): P14 named the missing
date as the fix and applied it to M5's capture, not here. One real hole —
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
`mvn dependency:tree` says otherwise: core's whole compile scope is **eight artifacts**, and
the aggregate contributes exactly one jar with **no new transitive dependency at all**. The
number is now recorded in [M0's verification block](milestones.md#m0--skeleton-and-ci).

> **Corrected 2026-08-28 by [P14](#p14--a-coherence-pass-over-the-tracked-documentation).**
> This said "six artifacts" and M0 copied it. The tree has eight: `langchain4j-core`, its
> three Jackson jars, `jspecify`, the aggregate, `com.typesafe:config` and `slf4j-api`. The
> finding this paragraph reports — that core is not accumulating dependencies and the
> aggregate costs one jar — is unaffected.

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
reads** — about two per million, on an AMD Ryzen 7 7840HS running Temurin 25. Rare enough never to show up in a normal run of
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

**Status:** Done 2026-08-27 · **Branch:** `docs/documentation-reorganisation`

Prompted by the owner failing to parse one of this repository's own sentences. The README
described the `AtomicSnapshot` example as *"One save changes two models; four threads sample
the pair both ways and count the mixed ones — via `snapshot()` the count is zero by
construction"*, which is accurate, short, and unreadable unless you already hold the mental
model it describes. The manual's version of the same sentence had the same problem.

**The distinction this settled, now recorded as
[ADR-0039](../adr/0039-user-facing-prose-is-written-for-a-non-native-reader.md).** Being
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
- **Nine metaphors** that decode only for a reader who already knows the answer: a guarantee
  with a "precise width", a boolean that would "bless" every configuration, the caching trap
  "in a new costume", a decision "welded to" the build, memory built with "no ceremony", a
  module on the community "release train", symlink handling that is "not a tidy-up
  candidate", seeing a guarantee "for nothing" (free, or pointless?), and capabilities that
  vary "depending on who you ask".
- **Nine vocabulary items above B2**, the largest being "straddle a reload" in five places:
  the README, `LlmRegistry`, `LlmSnapshot`, the CHANGELOG, and a line `AtomicSnapshot` prints
  at runtime. The replacement was not invented:
  *"a reload landing between them"* was already the phrasing used elsewhere in the same files,
  so the fix was to stop maintaining two ways of saying one thing. Also "hearsay", "inert",
  "page someone", "collectable", "three days in", "sized against", "in miniature", "bites you".
- **Five grammar problems, in eight sentences** — the imperative-as-conditional *"Resolve each
  file as you parse it … and this throws"* in the README and the tutorial; the dangling *"the
  ones that are"*, which never says what the ones in question are, twice in the reference;
  *"no caller to be thrown at"* in the README and in `LlmRegistry`; a doubly-nested relative
  clause about a file the operator expected to be read; and *"stops being entered"*. Grep
  those five over `git diff main..HEAD` to get the eight.

**Two smaller findings the owner ruled on separately.** `licence` appeared once against
`License` everywhere else and in the filename, now uniform. And the Java sample in *Why* uses
`claude-sonnet-4-6` while every configuration example uses `claude-sonnet-5` — deliberate,
because that sample's whole point is a `temperature` fixed in a builder call and
`claude-sonnet-5` rejects a non-default temperature with a 400 — found by
[P12](#p12--testing-the-examples-by-hand-and-a-live-break-in-anthropics-sampling-parameters)
running `ProviderSwap` by hand, not by P6, which this entry first credited in error and P12
caught. It read as an inconsistency, so the reason is now stated where `temperature` is
already discussed.

**One thing this task got wrong about itself, worth recording.** The sentence added to
explain the `claude-sonnet-4-6` choice was first written as *"a temperature welded into a
builder call"* — reintroducing, three edits later, the exact metaphor this task had removed
from that same file's opening section. Caught immediately, but it says something about the
failure mode: the register is a habit, not a decision made once, and a pass like this does
not inoculate the next paragraph written.

**Every number in this work was wrong at least once, including the ones this entry first
claimed were sound.** An earlier draft here said the counts of *what to fix* were all grepped
and held, and that only the counts describing *the fix itself* were estimated. That is
contradicted by the first row of its own table: "straddle" was a what-to-fix count, written
from impression, and wrong. Grepping happened for the lists of items; the totals over them
did not always follow:

| Claimed | Where | Actual |
|---|---|---|
| "straddle a reload" in **six** places | this entry, and commit `418ca5b`'s message | **five** |
| the old README sentence was **eleven** words shorter | ADR-0039, Context | **six** (26 against 32) |
| the README's `AtomicSnapshot` row grew by about **fifteen** words | ADR-0039, Consequences | **six** (35 against 41) |

The last two describe the same edit and disagree with each other, which is what exposed them.
Both sat in an ADR whose subject is careless writing, and the fifteen was in the sentence
justifying the rule's cost — overstating that cost by two and a half times while arguing it
was worth paying.

**The first correction was itself wrong, and a second check caught that.** Replacing
"fifteen" with the measured six, the rewritten sentence called that README row *"the worst
case in the whole pass"* — asserted, not measured, and false: the same example's row in
`part-2-reference.md` grew from 73 words to 99. The next attempt called *that* one the
largest single expansion, which was also wrong; the largest hunk in the commit is a 13-to-63
rewrite in the README, and it belongs to [P12](#p12--testing-the-examples-by-hand-and-a-live-break-in-anthropics-sampling-parameters)'s
`temperature` explanation rather than to this rule, because that one commit carried two
pieces of work. The paragraph now gives whole-file totals — 11,597 words to 11,752, 1.34% —
and names the two sentences it can attribute, with no superlative at all.

Three iterations to state one cost. Each wrong version was written while *correcting* the
previous wrong version, which is the part worth keeping: the impulse to reach for a vivid
figure survived being caught twice, and what finally stopped it was running the measurement
before writing the sentence rather than after.

**An independent review then found four more, and the fix for them produced one more still.**
A session with no memory of writing any of this text was asked to verify by measuring rather
than re-reading. It refuted the framing sentence above the table — an earlier draft claimed
the what-to-fix counts had all been grepped and held, which its own first row contradicts —
and falsified three further figures nobody had checked: the "welded" metaphor came back not
"fifty lines above" but roughly a hundred and fifty; the stale Javadoc line survived two
milestones, not three; and "fifteen precedents" for the ADR amendment convention is ten
ADRs, or twelve relationships, depending on how you count. Writing the CLAUDE.md rule that
came out of all this, the first draft said "four wrong versions in total", which matches
neither the chain of three nor the four one-off counts. It was caught by counting them into
a list before writing the sentence — which is the whole rule, arrived at the long way.

**A third review found that two of the three counts above were still wrong, and that one of
them was not a miscount.** The vocabulary figure held — nine items, with "straddle a reload" in
exactly five files. The other two did not:

- **Ten metaphors was nine.** The tenth, *a "shorter fuse" for a "flaky" connection*, was never
  in any file. `git log --all -S "shorter fuse"` returns only the two commits that *describe*
  this pass, and `-S "flaky connection"` returns nothing at all. It looks like the vivid version
  of a sentence that was **written** rather than removed: the tutorial's replacement text reads
  *"a shorter timeout for an unreliable local connection"*. So the write-up did not merely
  miscount what it had fixed — it credited itself with removing a metaphor it had just invented,
  and did so in the entry whose subject is writing figures without measuring them.
- **Six grammar sentences was five problems over eight sentences.** Six matches neither
  convention used by its two neighbours in the same list, which count distinct items. Three of
  the five appear twice ("Resolve each file as you parse it" in the README and the tutorial,
  "the ones that are" twice in the reference, "no caller to be thrown at" in the README and in
  `LlmRegistry`) and two appear once, which is 8.

Both were found by grepping the whole `main..HEAD` diff rather than the single commit the
earlier rounds had looked at — the pass spans more than one commit, and no earlier round had
read it end to end. The status-board figure moves from 25 to 23 with them.

The tally, for anyone tempted to treat this as a run of bad luck rather than a method failure:
**one figure wrong three times in a row, and seven more wrong once each**, the last two of them
above. Nothing that was measured before being written was ever wrong. And one of the eight is
not a counting error at all: "run the command that produces the figure before writing the
sentence" catches a wrong count, but only reading the diff catches a fix that never happened.

**They were corrected in place in ADR-0039, which its accepted status would normally
forbid.** The judgement: the freeze exists to stop a decision being rewritten after people
have relied on it, and this ADR had never left the branch it was written on, was not on
`main`, and was referenced from nowhere outside this repository — the same reasoning P13's
numbering note used to renumber an ADR at that stage. Leaving figures known to be false
inside an accepted ADR, in order to honour a rule against revisionism, would invert that
rule. The corrected sentences state the measurement rather than an adjective, so the next
reader can check them. Commit `418ca5b`'s message still says "six places" and is left alone:
a commit message is history, not a document.

The same judgement was applied a second time for the two counts above, under the same
conditions: still not on `main`, still unpushed, still referenced from nowhere outside this
repository. Two of the three figures in ADR-0039's Context are now measurements a reader can
re-run, and the Forces section no longer offers *"a shorter fuse"* as an example of an idiom
this repository removed, because it never contained it. What was **not** touched: ADR-0039's
"26 words to 32" for the README's `AtomicSnapshot` row. That figure was correct for the pass it
measures. The same cell is 38 words today, because a separate finding — the row claimed a
`get()` pair "occasionally does" catch a torn read, where five runs of the example gave 2, 0, 0,
1 and 0 — replaced the assertion with what the counter can actually promise. A later edit for a
different reason does not make the earlier measurement false, and does not license a third pass
over a frozen body.

**Two defects the same check turned up, neither of them a count.** Grepping every phrase this
entry claims to have removed against the current working tree found one survivor:
*"clears one set lower down"*, the native-speaker ellipsis the pass fixed in the README, was
left standing in `part-2-reference.md`'s key table and in the CHANGELOG. One of three
instances fixed, and the write-up said nothing about the other two, because the pass worked
file by file and the count was never taken across files. Both are now fixed. Separately, and
older than this branch: in `docs/adr/README.md` the `Superseded by ADR-NNNN` row sits *below*
the paragraph that follows the status table, so it renders as a line of literal pipes rather
than as a table row. `6e53dab` ([P8](#p8--status-line-drift-and-a-check-that-would-have-caught-it))
inserted that paragraph into the middle of the table. The row is moved back inside it.

The lesson is narrower than "verify claims", which this project already knew. It is that the
discipline was applied asymmetrically — rigorously to the subject of the work, not at all to
the description of the work — and the write-up is exactly where nobody thinks to grep.

Verified: `mvn clean install` and the full reactor suite green, offline; every edit is prose,
a comment, or one printed line, so no behaviour changed. `build/check-docs.py` clean.

---

### P12 — Testing the examples by hand, and a live break in Anthropic's sampling parameters

**Status:** Done 2026-08-27 · **Branch:** `docs/documentation-reorganisation`

Before touching anything for M6, the plan was to run the bundled examples by hand rather than
trust that they still worked.

**The premise first written here was wrong, and is corrected rather than deleted.** It said the
four provider modules had only ever been exercised through `-Pintegration verify`'s `ProviderIT`
classes, never through the code a reader actually copies.
[P3](#p3--the-manual) contradicts that: it ran the tutorial's ten steps live on 2026-08-23, the
day *before* [P6](#p6--the-integration-tests-against-live-apis) first reached a provider from an
integration test. What is true is narrower, in two parts. The examples module depends on OpenAI
and Anthropic only, so Gemini and GLM have never appeared in an example at all
([P4](#p4--two-examples-for-the-two-undemonstrated-strengths), *Not done*). And no run of
anything — example or test — had ever sent the combination that broke: a non-default
`temperature` to `claude-sonnet-5`. The conclusion this task reached is unaffected; only the
account of why the gap survived changes.

One thing that leaves unresolved. At P3's commit the tutorial's step 5 already set
`temperature = 0.2` on `claude-sonnet-5`, so either that step's chat was never actually sent —
P3 captured the menu there but no answer — or Anthropic's deprecation landed between
2026-08-23 and this task. Nothing in the repository decides it, and this entry does not guess.

#### Result

`AtomicSnapshot` (no key, no request) reproduced the [ADR-0038](../adr/0038-snapshot-gives-callers-the-atomicity-the-swap-already-has.md)
guarantee live: 2 mixed pairs in roughly 139 million reads through `get()`, 0 through
`snapshot()`. `mvn -Pintegration verify` passed on all four providers, no skips. Then, with
real keys, `ProviderSwap`'s Anthropic branch failed:

```
answer: [request failed: {"type":"error","error":{"type":"invalid_request_error",
"message":"`temperature` is deprecated for this model."}}]
```

#### What broke, and why the existing tests never caught it

Anthropic has deprecated non-default `temperature`/`top_p`/`top_k` on `claude-sonnet-5` (and
on Opus 4.8 and Fable 5): the model's adaptive thinking now controls its own sampling, and a
non-default value is a 400, not a warning. `AnthropicProviderIT` never saw this because it
targets `claude-sonnet-4-6` and sets no `temperature` at all — this is a parameter rejected by
one specific model, not an authentication or account failure, so nothing short of running that
exact combination live could have found it. Same shape of gap as
[P6](#p6--the-integration-tests-against-live-apis), one level down: there it was a model ID
nothing had verified, here it is a parameter nothing had sent.

#### Changed

`council.conf`'s `SL` and `SH` lost `temperature`; they already differed in memory and
streaming, so nothing new had to be invented to keep them distinct. `ProviderSwap.java`'s
`anthropic()` block lost its `temperature` line the same way. `README.md` and
`docs/manual/part-1-tutorial.md` were re-read against the fix and updated everywhere they
quoted the old blocks, including step 8's layering demo, which used to show `local.conf`
overriding `temperature` — it now overrides `timeout` instead, since that field survives
contact with the real API.

One unrelated drift caught in the same pass: step 4's shown console listing displayed `SL`'s
description as *"short and cheap — the everyday answer"* before the file had that description
— it still said *"my first model"* at that point in the walkthrough. Fixed to match the file
as written at each step.

#### A correction P11 needed, made after this entry pointed it out

[P11](#p11--the-user-facing-text-read-as-a-non-native-reader-would-read-it), landed in the
same commit as this task's fix, first explained the `claude-sonnet-4-6`/`claude-sonnet-5`
split in the README's *Why* section as "found by P6's live run." It was not: P6 ran
`claude-sonnet-4-6` with no `temperature` set and could not have hit this. The finding is
this task's, from running `ProviderSwap` by hand rather than the integration tests. Flagged
here rather than fixed directly, since the sentence belonged to an entry this task did not
own — a second session, working the same checkout concurrently, corrected P11's wording in
the time it took to write this paragraph.

Verified: `mvn clean install` green; `ProviderSwap`, `ThreeModelCouncil` and `ConsoleChat` all
re-run afterward and all answered. `build/check-docs.py` clean.

---

### P13 — Branch protection on `main`

**Status:** Done 2026-08-27 · **Branch:** `docs/documentation-reorganisation`

[ADR-0016](../adr/0016-one-feature-branch-per-task.md) left "merge strategy and whether pull
requests are used" open pending [D2](open-decisions.md#d2--repository-visibility). D2 has
been settled since [ADR-0034](../adr/0034-the-repository-is-public-before-it-is-released.md);
nothing on GitHub enforced either sentence in the meantime, though —
`repos/.../branches/main/protection` returned `404 Branch not protected`, and the repository's
only collaborator held unrestricted `admin` push access to `main`.

**Applied**, via `gh api --method PUT repos/.../branches/main/protection`: the five checks
`.github/workflows/build.yml` already runs (`JDK 17`, `JDK 21`, `JDK 25`, `docs consistency`,
`offline, no API keys`) are now required and must be current with the branch; a pull request
is required before merging, with the required approving-review count at `0`; force pushes and
deletion are disallowed on `main`. `enforce_admins` is `false`.
[ADR-0040](../adr/0040-protect-main-with-required-checks-not-required-review.md) records the
decision, including the one non-obvious finding: `enforce_admins` is all-or-nothing on
GitHub's side, so turning it off to avoid a fake single-maintainer review requirement also
exempts the sole admin from the force-push and deletion protections, not just the review gate.
Confirmed against GitHub's own documentation and changelog rather than assumed.

**Numbering note, and how the collision it predicted was settled.** This work was done on a
branch off `main`, whose history stops at P10 and ADR-0038, while a second unmerged branch
independently held its own P11/P12 sections and a file already named `0039-...md`, for
unrelated work. `build/check-docs.py` rejects any gap in ADR numbering, so neither branch
could leave 0039 free for the other: each had to take `main`'s actual next free number, and
this entry was written expecting the two `0039` files to collide and one to be renumbered by
whichever merged second. Task identifiers have no such tooling check, so P13 was chosen over
the technically-free P11/P12 to keep the collision confined to the one place a script forced
it.

That is what happened. The two branches were consolidated into this one, and this ADR was
renumbered to [ADR-0040](../adr/0040-protect-main-with-required-checks-not-required-review.md)
because the prose-register ADR was committed first and lands first in the merged history. The
rule this leaves for next time: **an ADR number is only safe once it is on `main`.** Two
branches that each take "the next free number" are both correct and still collide, and the
checker cannot warn about it, because from inside either branch nothing is wrong. Renumbering
is cheap while nothing is pushed and no ADR is referenced from outside the repository; it
stops being cheap after either.

**This ADR does not amend ADR-0016, and the markers saying it did were removed.** A later
coherence check on this branch added `Amends: ADR-0016` to ADR-0040's header and the matching
`Accepted — the merge strategy amended by ADR-0040` to ADR-0016's status, reasoning from the
dozen existing amendment pairs in `docs/adr/` and from ADR-0016 having no forward pointer to
where its open question was closed. That reasoning skipped the paragraph in ADR-0040's own
Context that had already considered and rejected it: ADR-0016's decision — one branch per
task, nothing direct to `main` — is untouched here, and the amend mechanism is for an ADR
that *narrows or widens* an earlier decision, not for one that supplies a decision the
earlier ADR explicitly declined to make. An independent review caught the header and the body
contradicting each other, and the markers were removed rather than the paragraph rewritten.

Recorded because the argument for adding them is a good one and someone will make it again.
The forward pointer a reader of ADR-0016 might want is real, but the amend fields are the
wrong instrument for it, and `build/check-docs.py` enforces them in pairs, so a marker added
on one side silently forces the other. The relationship is stated in prose in this ADR's
Context, which is where it belongs.

**Not done.** No non-admin collaborator exists yet to observe the protection actually gating
anything; today it is inert for the one person who can push.

---

### P14 — A coherence pass over the tracked documentation

**Status:** Done 2026-08-28 · **Branch:** `docs/documentation-reorganisation`

Asked to check whether the tracked documentation is coherent. `build/check-docs.py` was clean
and the reactor was green offline (core 63, reactor 96), so everything below is the class of
defect that check cannot see: a sentence that contradicts another sentence, or a number that
contradicts the command that produces it.

Carried on the same branch as [P11](#p11--the-user-facing-text-read-as-a-non-native-reader-would-read-it),
[P12](#p12--testing-the-examples-by-hand-and-a-live-break-in-anthropics-sampling-parameters)
and [P13](#p13--branch-protection-on-main), which is unmerged. Eight of the eleven files this
task edits are files that branch has already changed — `git diff --name-only main..HEAD`
against the working tree — so a second branch off it would have stacked and then conflicted on
the same paragraphs. Named after the work rather than after P14, per the convention note in
[`README.md`](README.md#conventions).

#### One API addition never reached the reference manual

`registry.snapshot()` and `LlmSnapshot` landed in
[P9](#p9--the-three-things-that-had-to-be-right-before-a-first-release) with
[ADR-0038](../adr/0038-snapshot-gives-callers-the-atomicity-the-swap-already-has.md). The
README, the CHANGELOG and `LlmRegistry`'s Javadoc all took the change. `docs/manual/part-2-reference.md`
did not: its *Using it* table went straight from `get()` to `names()`, `LlmSnapshot` appeared
nowhere on the page except one cell of the examples table, and *Reload semantics* still
described the pre-ADR-0038 world in which the swap's atomicity reaches the caller unaided.

That page is the one that promises "every configuration key, every public method". The
audit behind that promise — "every public API member (28 of 28)" in
[P4](#p4--two-examples-for-the-two-undemonstrated-strengths) — was run before P9 existed and
nothing re-ran it. **A completeness audit is only true on the day it runs.** P4 wrote it as a
permanent fact, so nothing signalled that adding a public method had invalidated it.

Fixed: a `snapshot()` row in the method table, and a new *One lookup, or several that must
agree* subsection carrying `LlmSnapshot`'s three methods, the two-per-million figure, the
per-unit-of-work rule and the pointer to ADR-0038.

#### The over-claim ADR-0038 corrected, still standing in two places

ADR-0038 exists because the README said *"the mixed pair never appears"* and the measurement
said otherwise. P9 quotes that sentence as the thing it fixed. Two copies of it survived:

- **`CHANGELOG.md`** — "four threads sample two models while one save changes both, and the
  mixed pair never appears". Now states both columns and what separates them.
- **`AtomicSnapshot`'s class Javadoc**, whose opening sentence said "nothing ever observes one
  of them updated and another not" fourteen lines above, in the same Javadoc block, a bullet
  explaining that a `get()` pair does exactly that. The lead sentence now names the boundary
  the example exists to show.

Both are inside [ADR-0039](../adr/0039-user-facing-prose-is-written-for-a-non-native-reader.md)'s
user-facing scope, and both were re-read by P11 for *register* without anyone checking them
for *truth*. Worth keeping: a prose pass and a correctness pass do not substitute for one
another, even over the same paragraph.

#### `get()` is no longer only "a volatile read and a map lookup"

The README and the reference both describe `get()` that way.
[P10](#p10--a-code-review-of-llmsnapshot-and-a-self-correction-found-while-checking-the-fix)
made `get()` delegate to `snapshot()` and explicitly asked whether that falsified those two
sentences. It cleared the question by verifying that `Collections.unmodifiableMap` does not
re-wrap a map that is already unmodifiable — which is true, and covers the map. It does not
cover the `new LlmSnapshot(...)` that `snapshot()` allocates on every call, and which
therefore now sits on every `get()`. "Cheap enough per request" still holds; the list of what
happens did not. Both sentences now name the wrapper.

The check that missed it was the right check applied to one of the two allocations in the
change. Nothing here argues for removing the delegation — the duplication P10 removed was
real, and the wrapper is a strong escape-analysis candidate.

#### Three counts that disagree with the command that produces them

| Claimed | Where | Actual |
|---|---|---|
| core's compile scope is **six** artifacts | [P7](#p7--closing-out-the-outside-review-of-the-public-repository), then copied into [M0](milestones.md#m0--skeleton-and-ci) | **eight** — `langchain4j-core`, three Jackson jars, `jspecify`, the aggregate, `config`, `slf4j-api` |
| **seven** modules: "parent, core, four providers, BOM, examples" | [M0](milestones.md#m0--skeleton-and-ci) | that list is **eight**; the seven are the `<modules>` entries, and `mvn` prints `[1/8]` |
| **six** publishable modules install with `-sources` and `-javadoc` jars | [M5](milestones.md#m5--release-readiness) | **five** produce those jars (core and the four providers); seven artifacts install, since the parent and the BOM install as POMs |

None of the three changes a conclusion — the aggregate still costs one jar, the layout is
still the planned one, and the examples module still publishes nothing. All three are
corrected in place with a dated marker, on P11's precedent.

The first is the interesting one. Six is the count in the *next paragraph* of the same
entry — the dependencies the aggregate declares, which is correct — so the figure was carried
up one paragraph by hand rather than mismeasured. The other two are enumerations that
contradict their own leading number, readable without running anything. **All three were
written in the same sentence as the list that refutes them**, which is a cheaper failure to
catch than P11's: no command needed, only counting the items already on the page.

#### `CLAUDE.md`'s amendment chain for ADR-0012 stopped short

Its *Snapshot-wide atomicity* paragraph read "(ADR-0012, widening ADR-0008)" and stopped
there, while `docs/adr/README.md` records ADR-0012 as *"Accepted — the width reaching a caller
amended by ADR-0038"*. `snapshot()` and `LlmSnapshot` appeared nowhere in the file at all, so
a session reading only `CLAUDE.md` would have learned the pre-P9 model of the reload boundary
and would have had no reason to keep `snapshot()` working.

Exactly the defect [P8](#p8--status-line-drift-and-a-check-that-would-have-caught-it) found in
the same file's dependency list, where the chain stopped at ADR-0020 without reaching
ADR-0028. It sat two lines under the sentence that predicts it — *"where a summary and an ADR
disagree the ADR wins and the summary is the bug"* — and both times the `docs/adr/` index was
the side that was right. The paragraph now names ADR-0038 and is followed by one that states
the boundary, including the wrapper allocation above.

#### Four smaller ones

- **`docs/manual/README.md` claimed the root README's examples table "is the single copy"**
  while `part-2-reference.md` carries a second table of the same four examples. The two have
  different jobs — cost against required credentials — so both stay and the index now says
  which is which.
- **Part 1's free/paid callout listed five offline steps** (1, 2, 6, 7, 8) while its own
  contents table and the manual index both say six of ten. Step 10 was the missing one.
- **M5's capture of the README's bundle table** was introduced in the present tense; its
  model identifiers have since moved twice (P6, P12). Dated.
- **P4's "243 million observations, no mixed pair"** had no forward pointer to P9, which
  refuted it — the convention this file uses everywhere else (M4 → P6, P11 → P12). Added.

`docs/tasks/open-decisions.md` was also reordered to D1, D2, D3, matching the status board;
it held D1, D3, D2. Its preamble now says all three are settled, which `CLAUDE.md` already
said and the file did not.

#### What this leaves for next time

Eight substantive findings, counted into a list before this sentence was written: the
reference's missing `snapshot()`, the two surviving copies of the "never appears" over-claim,
`get()`'s description, three miscounts, and `CLAUDE.md`'s truncated amendment chain.

Two findings, one substantive and one small, share a shape this repository has not been
watching for. Both P4's "every public API member (28 of 28)" and M5's "the README's own bundle table" were
**true on the day they were written and became false when something else changed** — no
miscount, nothing to re-measure, nothing a rule about running the command first would have
caught. The fix in each case was a date, not a number. That is the cheapest habit on this
list, and the only one here that costs nothing at all.

Verified: `mvn clean install` green, reactor 96/96, offline with no keys. `build/check-docs.py`
clean. Every edit is prose, a comment or a table row; no behaviour changed.

---

### P15 — A second coherence pass, and what the first one missed

**Status:** Done 2026-08-28 · **Branch:** `docs/documentation-reorganisation`

The same request [P14](#p14--a-coherence-pass-over-the-tracked-documentation) answered, asked
again the same day by a session with no memory of answering it. `build/check-docs.py` was
clean across 40 ADRs and 54 files, and `mvn clean verify` was green offline (core 63, reactor
96), so again nothing here is a defect either of those can see.

Carried on the same branch as [P11](#p11--the-user-facing-text-read-as-a-non-native-reader-would-read-it),
[P12](#p12--testing-the-examples-by-hand-and-a-live-break-in-anthropics-sampling-parameters),
[P13](#p13--branch-protection-on-main) and P14, which is unmerged. **All eight files this task
edits are files that branch has already changed** — `git status --short` compared against
`git diff --name-only main..HEAD` — so a second branch off it would have stacked and then
conflicted. Named after the work rather than after P15, per the convention note in
[`README.md`](README.md#conventions).

Five defects and two smaller items, counted into a list before this sentence was written. Two
of the five are in P14's own write-up, which is the part worth keeping.

#### The over-claim ADR-0038 corrected was standing in three places, not two

P14 found it in `CHANGELOG.md` and in `AtomicSnapshot`'s Javadoc and fixed both. The third
copy is the first place a reader meets the guarantee at all — point 3 of the README's *Why*:

> …atomically across every configured model at once, **so a flow using `SL` and `SH` together
> never sees one of them updated and the other not.**

The README states the opposite 334 lines further down, under *Hot reload*: a reload can land
between two consecutive `get()` calls, at roughly two per million pairs. Its own
`AtomicSnapshot` row says a `get()` pair *can* catch a mix.

`git log -L 57,61:README.md` dates it: the claim arrived with the README itself in M5 (#18),
as *"a multi-model flow never sees a new `SL` next to an old `SH`"*, and #23 rewrote the
sentence and kept the claim. The three copies are worded three different ways. The CHANGELOG's
repeats the sentence ADR-0038 and
[P9](#p9--the-three-things-that-had-to-be-right-before-a-first-release) both quote;
`AtomicSnapshot`'s is a near-paraphrase of it, in the file ADR-0038 is about; this one
restates the claim in a reader's own terms, in the pitch, far from where the guarantee is
explained. Searching for the wording the ADR uses reaches the first and stands a fair chance
at the second. Nothing about it reaches the third.

The paragraph now says what ADR-0038 actually gives: the swap happens in one step across every
configured model, so a half-applied configuration never exists, and a flow that needs two
models from one version of the file asks for a snapshot.

#### Two of P14's own fixes were not the fixes it recorded

Neither is a miscount, and neither is reachable by a grep. Both are P14's sentences read
against P14's diff.

- **"All three are corrected in place with a dated marker."** Two were.
  `grep -n "Corrected 2026-08-28" docs/tasks/milestones.md` returned two lines, both in
  [M0](milestones.md#m0--skeleton-and-ci). [M5](milestones.md#m5--release-readiness)'s "all
  six publishable modules install with `-sources` and `-javadoc` jars" was corrected to five
  with no marker at all. The marker is added, and names who added it.
- **"The fix in each case was a date, not a number."** P14 named
  [P4](#p4--two-examples-for-the-two-undemonstrated-strengths)'s "every public API member
  (28 of 28)" and M5's bundle-table capture as the two claims that were true when written and
  became false when something else changed. M5's capture got its date. P4's audit did not, and
  went on reading as a permanent fact — while `CLAUDE.md` was adopting the rule in the same
  commit, quoting that exact figure. It is dated now, with what falsified it: P9 added
  `snapshot()` and `LlmSnapshot` two days later.

#### Two counts that contradict the documents around them

- **`docs/manual/part-1-tutorial.md`, step 5: "Two of the four parts of a bundle are
  optional."** Three of the four are — `StreamingChatModel`, `ModerationModel` and
  `ChatMemoryProvider` — according to Part 2's *Concepts* row, the README's bundle table and
  `LlmBundle` itself. The step adds two of the three, and the sentence generalised that to the
  bundle. It now names all three and says where moderation is covered.
- **`CHANGELOG.md`: "Sources and javadoc jars attach to every published module."**
  `modelrack4j-bom` is a published module and produces neither — `modelrack4j-bom/target`
  holds no jar at all. This is the same over-claim P14 corrected in M5, one file over, and
  this copy is the user-facing one.

#### Two smaller ones in the reference

**The Anthropic `temperature` finding had reached every user-facing document except the
reference.** [P12](#p12--testing-the-examples-by-hand-and-a-live-break-in-anthropics-sampling-parameters)
recorded it in the README, the tutorial, the CHANGELOG and `council.conf`.
`docs/manual/part-2-reference.md` described `temperature` as 0.0–2.0 with no note, carried no
Anthropic entry among its per-provider notes, and had no troubleshooting row for the error a
reader actually sees. Same shape as P14's finding that `snapshot()` never reached that page: a
fact that landed everywhere except the page promising completeness. It gains a provider note,
a troubleshooting row, and a pointer from the key table.

**One cost taken deliberately.** Before this, `part-2-reference.md` named no model identifier
anywhere — grep for `claude-sonnet`, `gpt-5`, `gemini-` and `glm-` returned nothing. The new
note names `claude-sonnet-5` and `claude-sonnet-4-6`, so the reference now holds two strings
that can rot, and this project has already lost two model IDs that way
([P6](#p6--the-integration-tests-against-live-apis)). Both strings already appear in the README
and the tutorial, so the surface grows by one file rather than from zero, and the alternative —
stating the rule without naming a model — does not tell a reader whether it applies to the
model they configured.

**"Verified against all four live APIs" sat above a three-row table.** Anthropic is missing
from the table because it did not fail.
[ADR-0033](../adr/0033-provider-exceptions-pass-through-untranslated.md) words the same thing
without the mismatch — "Three of the four runs failed" — and the page now follows it.

#### What this leaves for next time

Both rules `CLAUDE.md` gains come out of P14 rather than out of the documentation P14 was
checking.

**A write-up's account of its own fix is subject to the same rule as the fix.** P11 established
"read the whole diff, not just a grep for the figure"; P14 quoted that rule and then described
two corrections it had not fully made. Neither error is a count, so nothing about measuring
before writing would have caught either — only reading P14's diff against P14's sentences did.

**An over-claim can survive in wording the ADR never uses.** Three documents made ADR-0038's
over-claim in three different forms, and the form furthest from the ADR's own wording is the
one in the README's pitch — the one a first-time reader meets first, and the last one any
search for the ADR's sentence would find. The passages that *make* a claim have to be re-read,
not only the ones that repeat its wording.

Verified: `mvn clean install` green offline, reactor 96/96. `build/check-docs.py` clean. Every
edit is prose or a table row; no behaviour changed.

---

### P16 — A third coherence pass, and the surface the first two searched past

**Status:** Done 2026-08-28 · **Branch:** `task/p16-javadoc-and-ci-comment-coherence`

The same request [P14](#p14--a-coherence-pass-over-the-tracked-documentation) and
[P15](#p15--a-second-coherence-pass-and-what-the-first-one-missed) answered, asked a third
time by a session with no memory of the first two, this time with
`docs/documentation-reorganisation` merged into `main` (#32). `build/check-docs.py` was clean
across 40 ADRs and 54 files and `mvn clean install` was green offline (core 63, reactor 96,
`[1/8]` through `[8/8]`), so as before nothing here is a defect either of those can see.

Two defects, both fixed. One further finding is recorded below and deliberately not fixed,
because it is an API question rather than a documentation one.

#### The over-claim ADR-0038 corrected was standing in a fourth place: core's own Javadoc

P15 found three copies — `CHANGELOG.md`, `AtomicSnapshot`'s class Javadoc, and the README's
*Why* — and fixed all three. The fourth is in
`modelrack4j-core/.../LlmRegistry.java`, in the class Javadoc's *Reload* section:

> There is no state in which one name's new configuration is visible next to another's old
> one.

`get()`'s own Javadoc, **fifty-two lines below it in the same file**, says the opposite:
*"two consecutive calls are not guaranteed to come from the same generation"*.
`git log -L 58,62:...LlmRegistry.java` dates the sentence to [M3](milestones.md#m3--hot-reload)
(#16), three days before
[ADR-0038](../adr/0038-snapshot-gives-callers-the-atomicity-the-swap-already-has.md) narrowed
the claim. It was not revised then, nor by either pass since.

This is the same shape P15 recorded — a lead sentence claiming the caller guarantee, with the
paragraph that refutes it further down the same file — and it survived both passes for a
reason worth writing down. **Every copy the first two passes found is in a document or an
example** — a changelog, `AtomicSnapshot`, a README. This one is in the library's
own public Javadoc, which is user-facing under
[ADR-0039](../adr/0039-user-facing-prose-is-written-for-a-non-native-reader.md) but is not a
file anyone opens when checking *documentation*. Reading the ADR's subject matter means
reading the class the ADR is about, not only the pages that describe it.

The paragraph now claims what ADR-0038 actually gives — a half-applied snapshot never exists —
and is followed by one that names the boundary and points at `snapshot()`.

**The same block had the second half of P15's finding too.** Its *Reload* section never
mentioned `snapshot()` at all, so the class-level overview of the API still described the
pre-[P9](#p9--the-three-things-that-had-to-be-right-before-a-first-release) world. That is
exactly the defect P15 found in `docs/manual/part-2-reference.md`, one surface over, and the
new paragraph closes it.

#### `build.yml`'s matrix comment gave the 21 leg a job ADR-0026 had replaced

The comment above `jdk: ['17', '21', '25']` explained 21 as *"it was the development JDK"* and
summarised the matrix as *"the floor, the dev JDK, and the current LTS"* — which is
[ADR-0026](../adr/0026-ci-matrix-is-floor-dev-jdk-and-current-lts.md)'s **title**, not its
Decision. The ADR's table gives 21 a live job the comment dropped: *"the most widely deployed
LTS among likely consumers."* Read against the workflow, the summary also names three roles
for legs where 25 does two of them, so 21 reads as vestigial.

That matters more than a stale comment usually would, because ADR-0026 makes this specific
comment load-bearing: *"the matrix is self-documenting — each leg's purpose is written into the
workflow, so the next person can tell whether a leg is still earning its place instead of
guessing."* The one leg a reader would have questioned is the one whose purpose had gone
missing. The comment now carries the ADR's three jobs, one line each, and the rule for what to
do when moving the 25 leg collides with the 21 leg.

Where a summary and an ADR disagree, the ADR wins and the summary is the bug — the same rule
`CLAUDE.md` states about itself, applied to a file nobody had thought of as a summary.

#### Not done: three public members no document mentions

`docs/manual/part-2-reference.md` promises *"every configuration key, every public method"*.
`grep -rn 'configurationName\|unknownType\|fromBlock' docs/ README.md CHANGELOG.md` returns
nothing, against three public members that exist:

| Member | Reachable by a caller? |
|---|---|
| `UnknownConfigurationException.configurationName()` | yes — in the `catch` the troubleshooting table sends readers to |
| `MemoryConfig.unknownType(String)` | only as an SPI implementer's error helper |
| `LlmConfig.fromBlock(String, Config)` | called from its own package only |

Only the first is a documentation gap. The other two look like accidental API surface —
neither has a caller outside `io.github.maxtrezzi.modelrack4j` — so the fix is plausibly to
narrow them rather than to document them, and narrowing a published member is an API change
that wants its own item and its own reasoning. Documenting them first would make the accident
permanent. Left open on purpose.

#### Re-verified clean, so the next pass need not redo it

Everything below was re-measured on 2026-08-28 rather than taken from an earlier entry, and
matched:

| Claim | How it was checked |
|---|---|
| the reference's *Every key* table is complete | 14 keys read by the loader, 14 rows |
| core's four compile dependencies, and the tree the reference prints | `mvn dependency:tree` — the aggregate still adds no transitive jar |
| `ProviderFactory` is "seven methods, three of which return `Optional.empty()`" | 7 and 3 |
| the five checks in `build.yml`, and JDK 17/21/25 | the workflow |
| `names()` sorted, the watcher's thread name, `OVERFLOW`, the one-second re-register retry, `close()` called from a listener, every logger name and level in the *Logging* table | read against the code |
| the three ADR-0038 copies P15 narrowed | still narrowed |

The completeness audit [P4](#p4--two-examples-for-the-two-undemonstrated-strengths) ran and
P15 dated is **true again as of 2026-08-28** for configuration keys (14 of 14); for public API
members it is true except the three above.

#### What this leaves for next time

**An ADR's blast radius includes the code it is about, not only the prose about it.** Three
passes over the same over-claim found copies in a changelog, an example, a README, and finally
the Javadoc of the class ADR-0038 exists to correct. That last one is the closest of the four
to the decision and was the last to be found, because each pass scoped itself to "the
documentation" and core's `src/main` is not filed under that heading.

**And a grep did not find it here either.** The sweep this pass ran —
`never (appears|observes|sees)`, `mixed pair`, `torn`, over `*.md`, `*.java` and `*.conf` —
returned the three copies P15 had already narrowed and missed this one, because "there is no
state in which one name's new configuration is visible next to another's old one" shares no
phrase with any of them. It turned up while reading `LlmRegistry` end to end to check
`names()`, `close()` and the logger names against the *Logging* table. That is the fourth
different wording of one claim and the fourth time a search for the wording failed, which is
the argument for reading the class an ADR is about rather than searching it.

**A comment an ADR promises will be self-documenting is a tracked document.** Nothing in this
repository treated `.github/workflows/build.yml` as prose subject to review, yet ADR-0026 wrote
a consequence that only that comment can deliver.

Verified: `mvn clean install` green offline, reactor 96/96, `build/check-docs.py` clean, and
the workflow still parses as YAML with the matrix unchanged (`yaml.safe_load`). Every edit is
a comment, prose or a table row; no behaviour changed.

### P17 — Mutation testing on core

**Status:** Done 2026-08-28 · **Branch:** `task/p17-mutation-testing`

PIT configured on `modelrack4j-core`, with the reasoning in
[ADR-0041](../adr/0041-mutation-testing-on-core-only.md). The point was never the score. This
library's promises are mostly negative — nothing swaps, nobody is notified, the flag is inert
— and a test that asserts "nothing happened" also passes when the code is broken in the
direction that makes nothing happen. Mutation testing is the only tool here that can tell
those apart, and what it produces is a list of places to read.

It found **four defects in the test suite and none in the code**. Every survivor was checked
against `src/main` before being classified, and in each case the production logic was
correct; what was missing was anything that would have complained if it had not been.

#### The tooling question, answered by running it rather than by recalling it

`pitest-maven` `1.30.0` and `pitest-junit5-plugin` `1.2.3`, both read from `maven-metadata.xml`
on Central. The versions are three years apart in spirit: PIT `1.30.0` was published
2026-08-27, the JUnit 5 plugin `1.2.3` on 2025-05-20, compiled against `pitest 1.15.2` and
`junit-platform 1.9.2`. This repository is on **JUnit `6.1.3`**, which neither of them knew
about, so whether the combination works at all was a real risk and not a formality. It works,
first try, with no downgrade.

Two mistakes in the first configuration, both found by running it:

- **PIT's `*` is not recursive.** `io.github.maxtrezzi.modelrack4j.*` silently skips the `spi`
  package. Both packages are now named.
- **A bare class name does not match a nested class.** `excludedClasses` said `ConfigWatcher`,
  and PIT mutated `ConfigWatcher$WatchedDirectory` anyway — four mutants, three of which timed
  out, which is exactly the noise the exclusion exists to prevent. It says `ConfigWatcher*` now.

#### The numbers

Three runs, each inside an isolated network namespace (below):

| | Mutants | Killed | Survived | Uncovered | Score | Test strength | Duration |
|---|---|---|---|---|---|---|---|
| Baseline | 113 | 102 | 5 | 6 | 90 % | 95 % | 3 min 56 s |
| After the four defects | 113 | 108 | 1 | 4 | 96 % | 99 % | 3 min 35 s |
| After the four smaller gaps, `toString` excluded | 112 | 112 | 0 | 0 | 100 % | 100 % | 3 min 24 s |

Timeouts stayed at 12, 12 and 11 — 10.6 % of the baseline, against a guard of 30 %. **Expect
the last two columns to move between runs of an identical configuration**: a timeout is a kill
decided by the clock, so the final configuration measured 11 timeouts in 3 min 24 s on one run
and 12 in 3 min 38 s on the next. The mutant total, the survivors and the uncovered count did
not move.

The suite went from 63 tests to 69: five went in for the four defects and one for the four
smaller gaps, because three of those four were assertions added to tests that already existed.

**As of 2026-08-28 there are no surviving and no uncovered mutants in core.** That is a
statement about this date and this configuration, not a standing property: it stops being
true the next time a class is added, and `mutationThreshold` is `0` precisely so that nothing
pretends otherwise.

#### A test that did not test what its name said

`LlmRegistryTest.moderationRejectedByProvider` is named *"a provider without moderation
rejects a config that enables it"* and asserted `hasMessageContaining("moderation")`. Deleting
`factory.validate(config)` from `SnapshotLoader` did not make it fail. `fake-remote` returns
`Optional.empty()` from `createModerationModel`, so the build step further down reports
*"produced no moderation model"* — which also contains the word. The test could not tell the
provider's own objection from an unrelated failure two steps later.

The consequence is larger than one test. That call is the **only** thing in core that invokes
`ProviderFactory.validate`, the SPI hook [ADR-0005](../adr/0005-provider-factory-spi-via-serviceloader.md)
gives a provider for objections core cannot make on its behalf, and whose Javadoc describes
the contract carefully. Nothing in the suite defended it: a refactoring that dropped the call
would have gone green.

Fixed on both sides. The assertion now names the factory's own wording, and a new
`FakeStrictProviderFactory` rejects every model name but one — an objection that can come from
nowhere else, so it pins the contract without depending on the moderation coincidence.

#### A test that looked symmetrical and was not

```java
assertThatThrownBy(() -> new MemoryConfig.MessageWindow(0))    // zero
assertThatThrownBy(() -> new MemoryConfig.TokenWindow(-1, false))  // minus one
```

Two parallel branches, two different values. The boundary is covered for message-window and
not for token-window, so `maxTokens <= 0` could become `maxTokens < 0` and nothing objected:
`max-tokens = 0` would have been accepted. Reading the block does not show this — the visual
symmetry is what hides the asymmetry of the values.

The fix is not the missing case. The bound is now a `@ParameterizedTest` parameter over
`{0, -1}`, so both variants are necessarily checked against the same values and the defect is
no longer expressible.

#### The memory a bundle hands out was never used

The three lambdas in `SnapshotLoader.buildMemoryProvider` were the only uncovered code in the
module. Four tests asserted `chatMemoryProvider()).isPresent()`; **none called `.get(memoryId)`**.
`max-messages` and `max-tokens` were read from configuration, passed to a builder and never
observed by anything: a wrong constant, or the two branches swapped, would have left the suite
green. The code is correct — this is the one place in the lot where an error would not have
been noticed.

Two tests now take the provider, ask it for a memory, add ten messages and check the
configured bound holds. **That the assertions are not vacuous was checked directly**, not
inferred from the mutants dying: raising the configured bounds from 3 to 50 while leaving the
assertions alone fails both tests with *"Expected size: 3 but was: 10"* and *"to be less than
or equal to 3 but was 10"*.

#### Two required keys nobody had ever left blank

`blankRequiredValuesAreRejected` exercised `api-key`. `LlmConfig`'s constructor calls
`requireText` on four keys, and removing the check on `name` or on `provider` broke no test. A
blank `provider` would then have failed much later, with a message about no provider module
being on the classpath — a true statement about the wrong problem. The test now covers all
four inside an `assertAll`.

#### Four smaller gaps, and one exclusion

| Mutant | Verdict |
|---|---|
| `SnapshotLoader:222` — a factory reporting `LOCAL` that supplies no estimator | The missing leg of a pattern that already existed: `FakeIncompleteProviderFactory` covered the same provider bug for streaming and moderation, not for the estimator. It does now |
| `UnknownConfigurationException.configurationName()` | Public API with no coverage — and the member [P16](#p16--a-third-coherence-pass-and-the-surface-the-first-two-searched-past) recorded as the one real documentation gap of the three it found. Asserted where the exception is already caught |
| `LlmBundle.name()` | Public API with no coverage. Asserted against the awkward-name case, where the key and the accessor could plausibly disagree |
| `SnapshotLoader:119` — `Collections.sort` on the provider list in an error message | Kept and tested rather than excluded: `ServiceLoader` order is unspecified, so without the sort the same mistake produces a differently ordered message on another machine. The new test asserts the tail of the message `isSorted()`, so it does not need editing when a fake is added |
| `LlmSnapshot.toString()` | **Excluded**, via `excludedMethods`. A debugging aid whose format nothing promises; killing it would pin that format against every later improvement |

#### The filename filter is an optimisation, not a correctness boundary

The most instructive survivor is one that no longer appears, because the watcher is now
excluded. Forcing `WatchedDirectory.accepts` to return `true` disables filtering by filename,
and `ReloadTest.unrelatedFileIsFilteredOut` **did not notice**. An event on an unrelated file
causes a re-read, the re-read produces an identical snapshot, and the
"identical content publishes nothing" rule
([ADR-0029](../adr/0029-reload-callbacks-are-quiet-contained-and-not-a-heartbeat.md)) makes the
difference invisible from outside.

So the filter saves work; it is the snapshot comparison downstream that provides the
guarantee. [ADR-0024](../adr/0024-watch-the-symlink-s-directory-not-its-real-path.md) settled
*how* to filter and is not contradicted by this — but nothing in `docs/` said which of the two
mechanisms the promise actually rests on, and a reader would reasonably have assumed the
filter.

#### What this says nothing about

Mutants are deterministic syntactic edits and do not explore thread interleavings. The
guarantee in the README that concurrent readers never observe a mixed pair of bundles
([ADR-0038](../adr/0038-snapshot-gives-callers-the-atomicity-the-swap-already-has.md)) is
untouched by any of these numbers, and `ReloadTest.getIsSafeDuringReload` — the test that
covers more code than any other in the suite, 401 blocks by PIT's own count on the final
run — proves no more
about it after this work than before. A mutation score of 100 % is not evidence of concurrent
correctness. The tool for that question is a stress harness such as OpenJDK's jcstress, which
this project does not have and which is not scheduled.

#### How the money guard was checked

Mutation testing runs the suite hundreds of times, and four of this repository's modules have
tests that call paid APIs. Rather than reason about it, all three PIT runs were executed
inside an isolated network namespace:

```
unshare -rn -- env HOME=... sh -c 'ip link set lo up; mvn -o ... mutationCoverage'
```

Loopback only, Maven offline, and the namespace verified to block egress before starting —
`/dev/tcp/151.101.0.0/443` answers `Network is unreachable`. **BUILD SUCCESS** in all three.
Independently: `modelrack4j-core` contains no `*IT.java`, the `integration` profile has no
`<activation>` block and `mvn -pl modelrack4j-core help:active-profiles` lists none, and a
grep for `java.net|HttpClient|Socket|URL(|http://|https://` over the whole of core's `src`
returns nothing.

#### The contributor-facing half, added after the fact

`CLAUDE.md` and ADR-0041 both carry the rule that PIT must never reach a provider module, and
neither is read by an outside contributor — the same gap `CONTRIBUTING.md` already covers for
ADR numbering. It now carries two things: the prohibition, next to the existing paragraph
about the offline build and the API keys, because both protect against spending money by
accident; and the expectation to run mutation testing after changing logic in core, as a
bullet directly under *"a test that cannot fail is worse than no test"*.

The wording of that expectation was corrected in review. Calling the tool **optional**
describes the mechanism accurately and misleads about the value, and it would have
contradicted the rule immediately above it, which `CONTRIBUTING.md` states as an obligation
rather than a choice. The trigger was corrected too: not *"when you add something that needs
tests"* but *"when you change logic in core"*. The three tests this task repaired were written
at [M1](milestones.md#m1--core-without-watching) (#14) and the two code paths they failed to
defend at [M3](milestones.md#m3--hot-reload) (#16), so nothing was being added when the
defects were found — which is exactly the case the narrower trigger would have missed.

#### Verified

`mvn clean install` green offline, 8/8 modules, core 69 tests and 102 across the reactor;
`build/check-docs.py` clean. `git status` confirms **no file under `src/main/java` changed** —
the diff is `modelrack4j-core/pom.xml`, three test classes, one new test fake and one line in
the `META-INF/services` file. PIT is not in `.github/workflows/build.yml` and adding it is a
separate decision, deliberately left open by ADR-0041.

### P18 — The distance between arriving and running something

**Status:** Done 2026-08-30 · **Branch:** `task/p18-examples-and-readme-ergonomics`

Four small things, one theme: what a reader has to get through before the library does
anything for them. None of it changes behaviour. One shell script is added; the only edits
under a `src/main/java` are three Javadoc comment lines in two examples, carrying the renamed
configuration file. No statement in any class changed.

#### The README showed Java and XML before it showed configuration

The first HOCON block was at **line 165 of 600**. Before reaching it a reader passed the pitch
at line 5 — *"Declare `SL`, `SH` and `CR` in a config file"* — then a **Java** snippet at line
32 (the `builder()` anti-example that *Why* argues against) and **two XML** `pom.xml` blocks at
127 and 147.

So a README whose whole argument is *configuration instead of code* showed code, then build
metadata, and only then configuration, 27 % of the way down.

The fix is not a reordering of *Quick start* — dependencies really do come first when someone
follows it. It is a six-line HOCON block and the two lines of Java that use it, placed
immediately after the opening paragraph, where the promise is made. **The first HOCON block is
now at line 11 of 624.**

#### `council.conf` named one of the two examples that read it

| Example | Configuration |
|---|---|
| `ThreeModelCouncil` | reads the file given as an argument |
| `ConsoleChat` | reads the file given as an argument |
| `ProviderSwap` | writes its own at run time, in a temporary directory |
| `AtomicSnapshot` | writes its own at run time, in a temporary directory |

Two of four, not all four — so the file was misnamed rather than overloaded. Renamed to
`examples.conf`, and its header comment now says which examples read it and which do not.

Renaming beat splitting because both readers want the same `SL`/`SH`/`CR` set: two files would
be 43 duplicated lines to keep in step, for no gain.

`council.conf` appeared **12 times across 6 files**. Nine were updated, in `README.md`,
`CHANGELOG.md`, `part-1-tutorial.md`, `ConsoleChat` and `ThreeModelCouncil`. **The remaining
three, in [P12](#p12--testing-the-examples-by-hand-and-a-live-break-in-anthropics-sampling-parameters),
were deliberately left alone**: that entry records what happened on a particular day, and
editing a past record to match a later rename would make it describe something that did not
happen. `check-docs.py` does not flag them because they are prose, not links.

#### Running an example took a command nobody would type twice

```
mvn -q -pl modelrack4j-examples exec:java -Dexec.mainClass=io.github.maxtrezzi.modelrack4j.examples.ThreeModelCouncil -Dexec.args=...
```

There is now **one script per example** at the repository root — `run-atomic.sh`,
`run-swap.sh`, `run-chat.sh`, `run-council.sh` — each with a `--help` that gives what that
example shows, what it costs, which keys it needs and the plain `mvn` command for Windows.

Four commands, one implementation. The first draft was a single `run-example.sh <name>`
dispatcher; four visible commands are better, because the examples are then discoverable from
a directory listing instead of by running something to ask what exists. Each of the four is
therefore a single `exec` line into `build/run-example.sh`, which holds the argument handling,
the per-example help and the checks below — 128 lines of code that four copies would drift out
of step. The argument for four commands is about the interface, not about the implementation.
Calling the shared script directly says so and exits.

Those checks are the three things the raw command gets wrong:

- **`mvn install` is a prerequisite, not a detail.** `exec:java` resolves `modelrack4j-core`
  from `~/.m2` and not from the reactor, so the script installs when the artifact is absent,
  and `--build` forces it — a stale install silently runs old library code.
- **A missing key is reported, not thrown.** The paid examples name the variables that are
  missing and point at `atomic`, which costs nothing, instead of failing inside a provider.
- **Arguments are checked before anything is announced.** An earlier draft printed *"this
  example sends real requests to a paid API"* and then rejected a mistyped file name, which is
  the wrong order to tell someone those two things in.

**There is no `.bat`, by decision.** This machine is Linux only, so a Windows script could not
be run even once, and the precedent in this repository is the macOS `WatchService` latency in
[Task 0.8](phase-0-verification.md#task-08--watch-strategy-spike): state the gap rather than
ship a plausible untested figure. The README, the reference and the script's own help all say
to use the underlying `mvn` command on Windows.

#### Where the Java idiom rule lives, and where it does not

The request was to record in the documentation that the `java-best-practices-modern` skill is
used. It is now recorded in exactly two places, and deliberately not in a third:

- **`CLAUDE.md`** already named the skill; the bullet said too little to act on. It now says
  the skill is version-aware and loads one profile, that this project is Java 17, and which
  constructs are therefore unavailable however new the local JDK is — pattern matching for
  `switch`, record patterns, virtual threads, `ExecutorService` in try-with-resources.
- **`CONTRIBUTING.md`** carries the same rule stated as an idiom rather than as a tool: write
  modern Java, target the floor, `maven.compiler.release` is 17.
- **Not the README.** It is written for someone using the library, who has no use for the
  authoring tools, and naming one there reads as promotion rather than as information.

The reasoning for the split is that a Claude Code plugin skill is unusable advice to an
outside contributor, who has no access to it — the same gap
[P17](#p17--mutation-testing-on-core) closed for the mutation testing rule. What a human
contributor can act on is the constraint on the code, so that is what `CONTRIBUTING.md` states.
The skill governs the Java in this repository, most recently P17's tests, which were written
under its Java 17 profile.

#### Verified

- `mvn clean install` green offline — 8/8 modules, core 69, reactor 102.
- `build/check-docs.py` clean across 41 ADRs and 55 tracked files.
- `examples.conf` confirmed inside `modelrack4j-examples-0.1.0-SNAPSHOT.jar` after the rename,
  read with `unzip -l` rather than assumed.
- The scripts exercised on every path they have: `--help` for an example that takes a
  configuration file and one that does not, a configuration file passed to an example that
  writes its own, a file that does not exist, `build/run-example.sh` invoked directly, and a
  missing key — the last in an isolated copy with the keys unset, because the guard has to be
  proven to stop *before* Maven starts. **`./run-atomic.sh` was then run end to end**, twice
  across the two script layouts, printing its two generations and its torn-pair counts.
- The paid examples were not run. Nothing in this task required it, and P12 is the entry that
  records them being exercised against live APIs.

### P19 — Configuration sources, and a reload the application can ask for

**Status:** Done 2026-08-31 · **Branch:** `task/p19-config-sources-and-manual-reload` ·
**Produced:** [ADR-0042](../adr/0042-read-configuration-from-sources-not-files.md)

The registry has taken `List<Path>` and nothing else since [M1](milestones.md#m1--core-without-watching).
The consuming application now needs a layer that lives in a database — a row of HOCON text —
layered over ordinary files. A database row has no path to parse and no directory to watch,
so two things have to change: what a layer *is*, and who is allowed to trigger a reload.

This item is **reading only**. Writing configuration back is what the request started from,
and it is deliberately left to a later item: written first, it would have been written against
`Path` and deepened the coupling this one removes.

#### What was measured before anything was decided

Four spikes against the real `com.typesafe:config` `1.4.9` jar in `~/.m2`, because the whole
design turns on what the library actually does rather than on what it is expected to do.

| Question | Answer |
|---|---|
| Can a resolved snapshot be rendered back to a file? | **No.** With `api-key = ${MY_SECRET}` in the source, `render()` emitted `"api-key" : "sk-REAL-SECRET-123"` — the secret in plaintext. It also reordered keys alphabetically and moved a trailing comment above the key it followed |
| Does `setShowEnvVariableValues(false)` fix that? | **No.** It emits the literal `"<env variable>"`: the secret is protected and the substitution is destroyed, so the file no longer loads |
| Does an *unresolved* layer round-trip? | **Yes.** Parsed without `resolve()`, `${OPENAI_API_KEY}` survives verbatim; written, re-read and resolved, the value came back from the environment. The secret never reaches the file |
| Is per-key provenance available? | **Yes**, `origin().filename()` and `lineNumber()` gave `base.conf:3` against `local.conf:1`. But `Config.getValue()` **throws** `ConfigException$NotResolved` on a substitution — the unresolved tree is reachable only by traversing `root()` |

That last row was measured on a plain parse, and **the loader as shipped does not behave that
way**: it passes `setOriginDescription(source.id())`, which replaces `filename()` with the id.
Measured on both routes afterwards, `filename()` is `null` and `description()` reads
`"etichetta: 2"` — so error messages keep their provenance, which is what the option is for,
while a future task wanting per-key provenance reads `description()` and `lineNumber()`, or
parses a second time without the option. The row is left as it was measured, with this note
under it, rather than rewritten to describe a spike that was not run.

`ConfigFactory.parseString` and `ConfigParseOptions.setOriginDescription` were confirmed present
with `javap`, so replacing `parseFile` costs no new dependency and keeps error provenance.

#### The shape, and the two proposals that lost

`ConfigSource` is `id()` plus `text()`, and names no file, path or URI. Notification is a
separate `ChangeNotifier`, which is today's `ConfigWatcher` behind an interface it already
fits — it is `AutoCloseable` and already takes a `Runnable onChange`.

Two earlier proposals were refuted in discussion, and both are recorded in ADR-0042 because
the reasoning generalises. `Optional<Path>` on the source put the filesystem back into the
new interface one level below where it had just been removed from four classes.
`Optional<URI>` was worse: an address makes the registry dispatch on scheme, which is the
boundary [ADR-0002](../adr/0002-scope-to-langchain4j-llm-configuration.md) draws. The test
that settled it was asking who needs to *act* on an address rather than print one — and in
this design, nobody does.

#### What it costs

`ReloadFailure` changes from `List<Path>` to `List<ConfigSource>`. That is a breaking change
to a public record, free today at `0.1.0-SNAPSHOT` with no tag and no published artefact, and
not free after [M6](milestones.md), whose preparation is running in parallel. The timing is
the reason this is being done now.

Reloads serialise behind a lock. A public trigger means the compare-then-swap in `reload()`
has a second writer, so the invariant that made it lock-free is gone — **on the read side
alone, before any write API exists.** Readers are unaffected: `get()` and `snapshot()` keep
their volatile read, so [ADR-0038](../adr/0038-snapshot-gives-callers-the-atomicity-the-swap-already-has.md)
is untouched.

#### What the work found

**A parse error was escaping as a third-party exception, and had been since M1.**
`LlmRegistry.build()` documents `@throws ConfigValidationException` for an invalid layer, and
`ConfigLoader` wrapped the failures from `resolve()` — but the parse call itself sat outside
that `try`, so a malformed layer threw `com.typesafe.config.ConfigException$Parse` at the
caller instead. Confirmed pre-existing rather than introduced here, by reading the same loop
on `main`. It is now wrapped, with the source's id and the line kept in the message. The
failure path was never broken by it: `reload()` catches `RuntimeException`, so a broken file
was always logged and reported — only the type a caller catches was wrong.

Nothing in [ADR-0033](../adr/0033-provider-exceptions-pass-through-untranslated.md) is
touched: that decision is about exceptions a *provider* throws, and translating a HOCON
failure is what this loader already did one line further down.

**The concurrency test was checked against the defect it names.** A test that asserts a lock
is doing something has to fail without the lock, or it is one of the tests
[P17](#p17--mutation-testing-on-core) found. With `synchronized` neutralised, the source's
high-water mark of simultaneous readers was **4 of 4 threads**, against the 1 the test
requires. Restored, it is 1.

**Three copies of a claim the lock made false, and one deliberately left.** "The watcher
thread is the only writer, so there is no lock anywhere in the reload path" was true until
this item and is now not. It was in `docs/manual/part-2-reference.md` under *Threading and
lifecycle*, and again in `LlmRegistry`'s own field Javadoc — the surface
[P16](#p16--a-third-coherence-pass-and-the-surface-the-first-two-searched-past) found the
fourth copy of an ADR-0038 over-claim on, which is why the code was searched and not only the
documentation. Both are fixed. The third, in [M3](milestones.md#m3--hot-reload)'s write-up, is
**left as written**: it records what was true when M3 shipped, and editing a past entry to
match a later change would make it describe something that did not happen — the same call
[P18](#p18--the-distance-between-arriving-and-running-something) made about `council.conf`.

#### A review of the code this item wrote, and what it found

The new classes were reviewed against the project's Java skill after they were green, not
instead of being green. Four findings, all in code written by this item.

**A silent swallow.** `reloadQuietly()` catches `RuntimeException` on the grounds that
`reload()` has already logged it and told the failure listeners — true for the load, which is
inside the `try`, and **false for the diff**, which was outside it. A failure in
`ReloadChange.between` would have been swallowed with no log and no listener called, which is
the worst of the two shapes: before this item the same failure escaped to the watcher thread,
loudly. The `try` now covers the diff as well, and stops before the swap, so the message it
prints — the previous configuration stays live — stays true.

**A notifier nobody could close.** `build()` assigned the notifier to the registry and then
started it. If `start()` threw, `build()` did not return, so the caller never received the
registry that owned it, and anything the notifier had allocated leaked with no reference left
to close it. It now closes the notifier and attaches a failing close to the original failure
rather than letting it mask it. `FileChangeNotifier` itself never leaked —
`ConfigWatcher`'s constructor already closes its watch service on failure, checked rather than
assumed — but the interface is public and a third-party notifier has no such guarantee.

**A guard that did not guard.** `FileChangeNotifier.start()` tested a volatile field for null
and then assigned it, so two callers could both pass the test and start two watcher threads,
one of them unreachable and never closed — against a Javadoc line promising
`IllegalStateException` on a second start. It also allowed a *closed* notifier to be started
again, because `close()` nulled the same field. Both are now one three-state transition under
a private lock, with the close itself performed outside that lock, because closing joins the
watcher thread.

**An unreachable check.** `ConfigSources.validated` null-checked each element after
`List.copyOf`, which rejects null elements itself — confirmed by running it rather than by
reading the Javadoc.

**A fifth, found only because the question was asked again.** The review reported four
findings and stopped. Asked whether everything was fixed, the same check-then-act pattern
turned out to be still sitting in `LlmRegistry.close()` — read the field, clear it, close it —
which is the pattern the review had just removed from `FileChangeNotifier` two files away.
Two threads closing at once could both call the notifier's `close()`, and the JDK is explicit
that `AutoCloseable.close()`, unlike `Closeable.close()`, **is not required to be idempotent**
(read out of `src.zip`, not recalled). With `ChangeNotifier` now a public extension point,
that is calling an implementer outside their contract. It is one `getAndSet(null)`.

The lesson is not about the defect, which is small. It is that a review that fixes what it
reports can still leave the same defect in a file it did not name, and that "have you fixed
everything?" is a different question from "what did you find?".

**What that fifth fix could and could not be tested for.** The window is two instructions
wide, so a test for it is probabilistic in a way the reload lock's is not. Measured against a
deliberately broken `close()`: one round caught it in **3 runs out of 5**, and 40 rounds in
**10 out of 11**. The first figure written into the test's Javadoc claimed 40 rounds caught it
*every time* — written before it was measured, and wrong at the second attempt. The test keeps
the measured numbers and says plainly that what makes the double close impossible is
`getAndSet`, not the test.

**Mutation testing then found that two of these fixes had no test.** The notifier-ownership
path came back `NO_COVERAGE` on both of its lines: a safety path added and never exercised,
which is precisely the case [ADR-0041](../adr/0041-mutation-testing-on-core-only.md) buys the
tool for. Two tests now cover it, including the one where the close *also* fails and must be
suppressed rather than lost.

#### Verified

- `mvn clean install` green offline. **8 modules** — parent, core, four providers, BOM,
  examples — and **125 tests**: core 92 (69 before this item, plus 23 new), and 7, 8, 8, 10
  across the four provider modules.
- **Exactly one existing test needed changing**, `ReloadTest`'s assertion on
  `ReloadFailure.configFiles()`, which now reads the sources' ids. Nothing else in the suite
  noticed, because `configFiles(List<Path>)` still means what it meant.
- `build/check-docs.py` clean across 42 ADRs and 56 tracked files.
- `./run-database.sh` run end to end, and its `--help` read: it prints the four `reload()`
  outcomes and ends with the previous configuration still live after the rejected one.
- **Mutation testing on core: 153 mutants — 151 killed, 1 timed out, 1 survived.** PIT's own
  headline says "Killed 152" because it counts a timeout as detected; the breakdown above is
  from `mutations.xml`, and the two ways of counting are why an earlier draft of this line
  claimed a total that did not add up.
  **The survivor is equivalent.** `EmptyObjectReturnValsMutator` replaces whatever an object
  method returns with an empty or default instance of that type — for `Optional`, with
  `Optional.empty()`. It landed on `LlmRegistry.reload()`'s own `return Optional.empty()`, so
  it rewrote that line to itself: the mutant and the original are the same bytecode, and no
  test can distinguish them. The line is covered — `unchangedReloadIsEmpty` asserts exactly
  that path — so this is a known PIT artefact rather than a gap in the suite.
  **The timeout is the one ADR-0041 predicts.** `NullReturnValsMutator` makes
  `chooseNotifier` return null, the registry then watches nothing, and a waiting test hangs:
  "a hung minion, not a finding".
  The report has to be read on a tree that has stopped changing. The first run's line numbers
  were produced while the source was still being edited and were unusable, which is why every
  figure here comes from a re-run.

#### A fourth review pass, and the claim that was measured false

Run with the `java-best-practices-modern` skill on the settled branch. Five findings, and the
first one is the interesting one because **the manual asserted the opposite of what the code
does, and the write-up said so in wording the Javadoc never used**.

**`close()` called from a reload listener does not work, and the manual said it did.** A
listener runs inside `reload()`, which holds `reloadLock`. `close()` then stops the notifier,
and a notifier's `close()` waits for its own thread — which may be waiting for that same lock.
Both halves were measured, with throwaway probes in core's test tree:

| Path | Measured |
|---|---|
| A `ChangeNotifier` whose `close()` calls `Thread.join()` with no timeout | **Deadlock.** `reload()` had not returned after 15 s; only `shutdownNow()`'s interrupt released it |
| The built-in `FileChangeNotifier`, via `watch(true)` | **`close()` took 5001 ms**, then returned — `ConfigWatcher.CLOSE_TIMEOUT_MILLIS` exactly |

The manual said, in the threading list: *"`close()` waits for a reload already in flight, so
no listener runs after it returns. It is safe to call from a listener — that case is detected
rather than deadlocking."* Both sentences are false, and `LlmRegistry.close()`'s own Javadoc
already said the opposite of the first one. The likely origin is a real guard read too
broadly: `ConfigWatcher.close()` does detect being called *on the watcher thread* and returns
instead of joining itself, and that same-thread guard was generalised into a claim about
listeners in general. That is the shape ADR-0038's over-claim had — a true narrow statement
restated as a wider one — so `ConfigWatcher`'s comment now says what the guard does not cover,
next to what it does.

The fix is contract, not structure. Running listeners outside the lock would remove the
hazard and lose the ordering between two reloads' listeners, which is the guarantee the lock
was added for. So the rule is now stated in the four places a reader can meet it:
`LlmRegistry.onReload`, `LlmRegistry.close()`, `ChangeNotifier`'s `@implSpec` — which is what
an implementer actually reads, and which never said `close()` may be called mid-reload — and
the manual's threading list.

**`ConfigSource` never told an implementer that `include` does not work.** The most silent
failure in ADR-0042: in a layer that is not a file, an `include` is looked up on the classpath,
finds nothing, and adds nothing, with no error and no log. It was documented in the manual and
in `ConfigLoader.parse`'s `@implNote` — which is package-private and never reaches published
Javadoc — but not on `ConfigSource.text()`, where someone writing their own source reads. The
`instanceof FileConfigSource` branch can only ever match `ConfigSource.ofFile`, because
`FileConfigSource` is package-private, so a hand-written file-backed source loses its includes
too.

**Three smaller ones.** `FileChangeNotifier.of` threw `ConfigValidationException` for a
non-positive `Duration` while `Builder.debounce` threw `IllegalArgumentException` for the same
value — two public doors, one invalid argument, two types; `of` now throws
`IllegalArgumentException`, with a test asserting both doors agree, and the empty-file-list
case stays `ConfigValidationException` because that is a statement about the configuration.
`LlmRegistry.close()`'s Javadoc contained *"the notifier is closed exactly once whoever
calls"*, which is not a sentence, inside a six-sentence paragraph — against
[ADR-0039](../adr/0039-user-facing-prose-is-written-for-a-non-native-reader.md), which governs public Javadoc.
`DatabaseSource.models(String...)` took name and model as alternating positional strings, so
an odd argument list read past the end of the array; it takes a `Model` record now. That last
one is example code, which is the shape a reader copies.

**Nothing in this pass was a defect in the shipped behaviour.** Four of the five were contract
and prose, and the fifth changed an exception type nobody could have depended on at
`0.1.0-SNAPSHOT`. The deadlock is reachable only through a documented-as-forbidden call, and
it is now documented as forbidden.

#### The commands that launch the examples

Checked after the fifth example was added, because
[P18](#p18--the-distance-between-arriving-and-running-something) built the launcher for four
and P19 made it five. The five scripts, their main classes, the Windows `mvn` commands they
print and their refusal paths were right. Three things were not.

**The reference said `ConsoleChat` needs "one provider key". It needs two.** The shipped
`examples.conf` puts `SL` and `SH` on anthropic and `CR` on openai, all three with mandatory
substitution, so the registry fails to build before any request is sent. Run with only
`ANTHROPIC_API_KEY` set, the message is `Could not resolve substitution to a value:
${OPENAI_API_KEY}` at `examples.conf` line 40. The script had always demanded both; it was the
manual that disagreed with it, and the script was right.

**A configuration path was passed to Maven exactly as typed**, while `exec:java` runs from the
repository root. A path relative to the directory the caller was standing in therefore passed
the script's existence check and was then not found — the check looked in two places and the
run looked in one. Paths are resolved to absolute before the exec now, repository-relative
first so the paths `--help` prints keep working from anywhere. A path containing a space is
refused with a message, because `-Dexec.args` splits on whitespace and the example would
receive two paths.

**`run-council.sh --help` promised layering that `ThreeModelCouncil` does not do.** It reads
`args[0]` and rejects anything else, so a second file made it print its own usage line after
Maven had started. Only `ConsoleChat` layers; the help now says so per example, and the extra
file is refused by the script before Maven runs.

Two smaller ones: the scripts pointed anyone missing a key at `./run-atomic.sh` as *the* free
example, which stopped being true when `DatabaseSource` arrived, and the `--help` now says
that a `.env` file in the repository root is loaded if present — worth stating plainly,
because a key left there is used without being asked for.

#### What this item changed in `CLAUDE.md`

Two commits on this branch touch no P19 code and belong to it anyway: they add three rules to
*Working practices*, each one paid for by a defect above, so the branch carries them rather
than leaving the lesson in a commit message nobody re-reads.

- **Reading the diff is not reading the file**, the next rung on the ladder that already went
  grep → diff. `056360d` added a case at line 28 of `build/run-example.sh`; the lines it made
  stale were at 76 and 139, and that diff's last hunk ended at 55.
- **Prose that was true when written is what later code falsifies.** Both false statements
  found in the manual were correct when committed — P3's and P4's — and were falsified by P19
  and P18. Grep cannot find these, because the stale sentence and the new mechanism share no
  vocabulary.
- **And some of it was never true**, which needs a different check again: a wrapper makes
  claims about code it does not contain, and `run-council.sh`'s promise of layering was
  contradicted by an `args.length != 1` that had been there since M2.

#### Carried over to the write item

Two findings that belong to writing rather than to this item. Writing a whole block to the
top layer would silently pin values inherited from below, freezing them against later edits
to the lower layer — `origin()` is what tells us which keys to write instead. And suppressing
the reload event for a self-inflicted change needs no flag: applying before writing leaves the
watcher's later diff empty, and `reload()` already returns early on an empty diff
(`LlmRegistry.java`, the `change.isEmpty()` guard).

---

### P20 — Writing a configuration layer back

**Status:** Done 2026-09-01 · **Branch:** `task/p20-writing-configuration-back` ·
**Produced:** [ADR-0044](../adr/0044-store-a-layer-back-as-text-validated-before-it-is-stored.md)

The write half of the request [P19](#p19--configuration-sources-and-a-reload-the-application-can-ask-for)
answered the reading half of. `LlmRegistry.store(target, text)` validates a new layer text
against the whole configuration, applies it, and only then stores it;
`storeIfUnchanged(target, expected, text)` does the same while the layer still holds the text
the change was based on. Both take a `WritableConfigSource`, which
`ConfigSource.ofWritableFile(Path)` provides for a file.

#### The edit API that was built, and then removed

The item shipped a fluent per-key editor first — `registry.edit(layer).set(…).commit()`, 302
lines with 440 lines of tests, reviewed and corrected — and then deleted it in favour of
passing the text. The owner's question is what turned it over: *"sembra che il salvataggio
fosse a volte solo parziale."*

**The file was never written partly.** `render()` produced the layer's whole text and the
write was temp-file-then-rename. What was partial by design was the *content*: the layer holds
only its own keys, never the merged result, because writing the merge would freeze every
inherited value in the higher layer. Correct, and from outside the API indistinguishable from
a save that dropped things.

That was the visible symptom of a deeper one: the library had an opinion about what the
application should write. Three more consequences came from the same root — a layer with an
`include` had to be refused outright, `set` needed a second method `setSubstitution` because
it could not tell `"${X}"` from `${X}`, and the layer came back canonicalised. All four are
gone now. What the library kept is the part an application cannot reproduce from outside: the
order, and the rollback. [ADR-0044](../adr/0044-store-a-layer-back-as-text-validated-before-it-is-stored.md)
carries the argument.

#### What the compare-and-set had to answer

A plain `store` is atomic against reloads and against other stores, and still cannot hold the
caller's read and its own write together. Two tests state both halves. The loss is shown
deterministically with latches, not with timing; the fix is shown at 25 rounds of two writers
appending different lines and both surviving. Disabling the comparison in the registry
(`current.equals(current)`) fails five tests, the concurrency one at round 0.

`StaleLayerException` is not a `ConfigValidationException` on purpose: a lost race is
retryable by the program, a validation failure is for a person.

#### What the reviews found

The first review, in the previous session, found three defects in the edit API — two
concurrent commits losing one change in 199 of 200 rounds, a symlinked layer replaced by an
ordinary file, and permissions narrowed from `rw-r--r--` to `rw-------` — plus two staged
classes that were implicitly public because they were declared inside an interface, the same
gotcha [P16](#p16--a-third-coherence-pass-and-the-surface-the-first-two-searched-past) found
in `MemoryConfig.unknownType`.

The second review, over the rewrite, produced eleven findings, all applied. Two mattered:

- **A plain `write(String)` inherited a refusal it did not need.** The include check sat in
  `stage()`, which `write()` shares with the store path, so a direct file write with an
  include through a symlink was refused although it validates nothing. Confirmed by running a
  throwaway test, not by reading. The check moved to `StagedFileWrite`, which is the path that
  validates.
- **The destination was resolved twice**, once in `stage()` and once in `commitStaged()`. A
  ConfigMap swaps its link whenever it likes and does not wait for the reload lock, so the two
  answers can differ: the staged file is written beside one directory and moved onto another,
  carrying permissions copied from a file it no longer replaces. It is resolved once now and
  carried in a `StagedFile` record. `stage_resolvesTheDestinationOnce` pins it, and putting
  the second resolution back fails exactly that test.

The rest were the documentation the code had outgrown — `LlmRegistry`'s class Javadoc still
said configuration is picked up "in one of two ways" and never mentioned that the registry can
write — plus the `write()` contract the rollback silently assumed, which is now stated: **an
implementation must store nothing at all if it throws.**

#### Mutation testing

Run by hand on core, twice, per [ADR-0041](../adr/0041-mutation-testing-on-core-only.md) and
[ADR-0043](../adr/0043-keep-mutation-testing-out-of-ci.md). The first run scored 187 of 192
and found three gaps in the suite, none in the code — matching
[P17](#p17--mutation-testing-on-core)'s pattern:

| Not killed | What it meant |
|---|---|
| `WritableFileConfigSource.write` ×2 | The public `write(String)` was exercised by no test at all: the store path reaches `stage`/`commitStaged` directly through `StagedFileWrite` and never calls it. |
| `StagedFileSource.text` | Never called by anything. It is a `FileBacked` source, so the loader parses it through its file, and `text()` is the unused half of the interface. |
| `destination`'s `catch (IOException)` | Never entered. |

Four tests closed the first two. The final run is **192 of 194**, and the three that remain
are explained rather than open: `LlmRegistry.reload:339` is an **equivalent mutant** — the
line is literally `return Optional.empty();` and the mutator replaces the return value with
`Optional.empty()`, so no test can kill it, and the real contract is asserted in
`ConfigSourceTest:109` and `:137`; `Builder.chooseNotifier:755` times out, which PIT counts as
killed; and `destination`'s fallback needs `Files.exists` to be true and `toRealPath()` to
throw immediately afterwards. That last one is now commented and logged, and left uncovered on
purpose.

#### Two rules the item changed

**A measurement carries the machine it came from.** The owner rejected the watcher latency
figures in the README and the manual: they said "Measured on Linux (inotify, Temurin 25)" and
named no hardware. *"O metti l'hardware o non lo citi."* Dropping the number would leave an
unmeasurable adjective, so the machine went in instead — AMD Ryzen 7 7840HS, ext4 on NVMe,
Pop!_OS 24.04 (kernel 7.0.11), Temurin 25 — beside every copy of the latency figures, the
~2.5 ms event burst the 300 ms debounce is sized against, and ADR-0038's "about two per
million" mixed reads. Each says in the same breath that one machine is not a benchmark.
`CLAUDE.md` carries the rule.

**An accepted ADR is immutable in its substance, not in a detail supplied later.** Completing
that last figure meant editing ADR-0038, which
[ADR-0015](../adr/0015-track-work-items-in-docs-tasks.md)'s practice forbade outright. The
owner ruled that immutability lives in the substance. The exception is exactly one thing — the
*conditions* of a figure the ADR already quotes may be completed in place, because supplying
them adds nothing to the argument and changes no decision — and it is not a licence to append
a finding, a date or a correction. Both `CLAUDE.md` and `docs/adr/README.md` state it with its
limit.

It does **not** reverse the ruling of 2026-08-25, which took a confirming measurement out of
ADR-0020's body and put it in M0's verification record — the `Re-measured 2026-08-25` block
in [M0](milestones.md#m0--skeleton-and-ci). Appending a finding is still forbidden, and that
is still where a finding goes. What changed is narrower than that ruling, not opposite to it.

#### The documentation and the example

`DatabaseSource` was the example this reached, and it was teaching the weaker pattern: it
saved into the row and then reloaded, and its step 4 left the row holding invalid text with
nothing pointing at a better way. It now runs six steps — the four answers `reload()` gives,
then the same rejected change through `store()`, which refuses it before the row is written,
then a valid one applied and stored in one step. Running it caught two sentences the change
had just falsified: the opening line still said every change below is applied by calling
`reload()`, and the report helper printed "reload() returned…" for a store. The two lines that
describe the row's contents now read the row instead of repeating a value by hand.

Two things found while checking the documentation, neither of them caused by this item.
`build/run-example.sh:32` still told `--help` that the example "Shows all four answers reload()
can give" — a wrapper describing code it does not contain, the
[P18](#p18--the-distance-between-arriving-and-running-something) defect. And
`docs/manual/README.md` said *"the one worth knowing here: `AtomicSnapshot` is free"* while
`build/run-example.sh:88` has said for some time that two examples are free.

One sentence P20 made false and then made true again: the tutorial calls Part 2 "the full
API", which stopped holding the moment `store` existed and holds again now that the reference
documents it. One that was simply false: the reference said "Those three are the library's
own" of the exception table, and there are four.

#### Verified

- `mvn -B clean verify` green with every provider key unset in the environment; **137 tests in
  core**, 45 of them in `ConfigStoreTest`.
- `javap` over the built jar: **19 public types**, against 17 on `main` — the branch adds
  `WritableConfigSource` and `StaleLayerException`. The branch's earlier commits also stood at
  19, with `ConfigEdit` where `StaleLayerException` now is, so the net count is unchanged
  across the rewrite and only against `main` is it +2. `StagedFile` is package-private, so the
  P16 gotcha did not recur.
- Mutation testing: 192 of 194, survivors accounted for above.
- Every fix in both reviews checked by reverting it and watching a named test fail.
- `./run-database.sh` run end to end; `./run-database.sh --help` re-read after editing it.
- `build/check-docs.py`: 44 ADRs, 58 tracked markdown files, no problems.

---

### P21 — What the library leaves available, said out loud

**Status:** Done · **Branch:** `task/p21-advanced-langchain4j-stays-available`

The documentation reads as if the advanced parts of LangChain4j were unavailable to a
modelrack4j user. They are not. The library instantiates the models and hands them over;
`AiServices`, `@Tool` methods, RAG retrievers and guardrails keep working exactly as they do
on plain LangChain4j, because a bundle holds the same `ChatModel`, `StreamingChatModel`,
`ModerationModel` and `ChatMemoryProvider` those APIs already take as inputs. What is out of
scope is *configuring* them from a HOCON file, not *using* them.

#### Where the wording misleads

- `README.md:85` — "It deliberately does not do prompt templating, `AiServices`, tools, RAG,
  retries or fallback", in the middle of the pitch. True about the library's own job, and
  read as a limit on the reader's application.
- `README.md:622` and `docs/manual/part-2-reference.md:808` — both **Out of scope** lists
  head the bullet with "`AiServices`, `@Tool` methods, RAG retrievers, guardrails". The
  second half of that same bullet already says the right thing ("This library builds the
  inputs you hand to `AiServices`; it does not wrap it") — it just arrives after a heading
  the reader has already parsed as "not supported here".

All three line numbers were re-checked against `main` at `8232bf3`; they move whenever those
files are edited, so read the quoted words rather than trusting the number.

[ADR-0003](../adr/0003-bundle-holds-config-shaped-inputs-only.md) is not being reopened and
the scope does not move: this is wording plus a demonstration, so it produces no ADR.

#### Two halves

1. **Prose.** Separate "not configured here" from "not usable" wherever the boundary is
   stated — the README pitch, both **Out of scope** lists, and anywhere else the manual
   draws it. Keep the boundary itself intact; the fix is a sentence saying what the caller
   still does with the bundle, not a softer scope.
2. **Code.** Show it: wire a bundle into `AiServices` with an interface and a `@Tool`
   method, and — the part only this library can show — call `registry.get(name)` per request
   so the AiService is rebuilt on the current bundle and survives a reload. Where it goes is
   the next section.

#### Prefer evolving an example over adding one, and consider removing some

The owner's steer, 2026-09-01: **the set of examples is itself a candidate for a clean-up,
and this item must not grow it by default.** Five mains exist today — `AtomicSnapshot` (244
lines), `ConsoleChat` (371), `DatabaseSource` (233 — this entry first said 181, corrected
2026-09-01 by `wc -l` at `8232bf3` and at `a143b43`), `ProviderSwap` (164) and
`ThreeModelCouncil` (86). P20 added no example, so five is still the number. Before writing a
sixth, ask which of the five still earn their place, whether any two are the same
demonstration in different words, and whether the `AiServices` part belongs inside one that
already exists.
`ConsoleChat` is the obvious host — it already loops, and already calls `registry.get(name)`
once per turn, which is exactly the habit an AiService rebuilt per request has to show.

Removing an example costs the same coordinated update as adding one — the script, its
`--help`, `build/run-example.sh`, and the lists in the README and the manual — so settle the
whole shape of the example set in one pass rather than adding here and pruning later.

#### Checked in advance

`dev.langchain4j:langchain4j:1.19.0` is already `compile` scope on `modelrack4j-examples`,
transitively through core's aggregate dependency
([ADR-0020](../adr/0020-core-depends-on-langchain4j-aggregate.md)), and
`dev/langchain4j/service/AiServices.class` is in that jar. So the example needs **no new
dependency** (`mvn -o -pl modelrack4j-examples dependency:list`, 2026-09-01).

#### If it becomes a new example

`build/run-example.sh` and its `--help`, a `run-*.sh` at the repository root, and every list
of the examples in the README and the manual all have to move together. P18 shipped a
launcher whose help described code it did not contain, and P19 left the launcher's "the free
example is `./run-atomic.sh`" line stale in two places a diff never showed.

#### The example set: five stays five

Nothing was added and nothing was removed. Each of the five pins a claim no other one pins:
`AtomicSnapshot` the torn read between two `get()` calls, `DatabaseSource` a layer that is not
a file and the `store()` order, `ProviderSwap` the provider changing under a call site that
names none, `ConsoleChat` the interactive whole, `ThreeModelCouncil` several names answering
one question. The closest pair is `ConsoleChat` and `ThreeModelCouncil` — both loop over
`names()` and both read capabilities off the bundle — and they still part company on the thing
each exists for: the council asks all three the *same* question in one run, which is the
scenario the README opens with, and it does it in 86 lines. Removing it would cost the
coordinated update in five places and buy nothing.

So the `AiServices` demonstration went inside `ConsoleChat`, as the entry above expected. It
is a `/tools` command: typed during a chat it toggles the answering path, and the questions
that follow go through an `AiServices` proxy built on the bundle *that turn* fetched, with one
`@Tool` method (a clock) that prints when it runs. The toggle resets on entering a
configuration, the same way the memory does. Three things made it worth putting here rather
than in a sixth main:

- The per-turn `registry.get(name)` was already there, so the AiService inherits it. The
  point being demonstrated — an assistant built once at start-up holds that snapshot's
  `ChatModel` for ever — is the cached-bundle mistake one level up, and it is visible only
  next to code that does the right thing.
- The bundle's `ChatMemoryProvider` goes straight into `AiServices.chatMemory(...)`, which is
  what [ADR-0004](../adr/0004-expose-chatmemoryprovider.md) exposed it for, and the same
  `ChatMemory` serves both paths — so toggling mid-conversation keeps the history. Verified
  live, below.
- No new script, no new `--help`, no new row in the two example tables: the update was one
  sentence in each of the places that already describe `ConsoleChat`.

The AiService path calls `chatModel()` even when the configuration also builds a streaming
model. An AiService streams by declaring a `TokenStream` return type, which is a second method
rather than a second object, and a demonstration of `@Tool` does not need it.

#### The wording, and a fourth place the entry had not found

The three passages the entry named were all fixed: the README pitch, and both **Out of scope**
lists. The fix is the same in each — the scope boundary is now stated as *configuring* those
features from a file, and the sentence that says using them is unaffected sits in the bullet
rather than after it. `README.md` and `part-2-reference.md` each gained a section, **What you
still write yourself**, with the `AiServices.builder(...)` code and the rule that it is built
at the point of use.

Two further places said it, and only one was findable by grepping for the words the entry
quoted:

- `CONTRIBUTING.md` — "`AiServices`, tools, RAG, retry and fallback pools are all on that
  list". Same defect, different wording, in the file an outside contributor reads *first*.
- `CLAUDE.md`'s own scope-boundaries list, which is exempt from the register rule but not from
  being current. It now separates configuring from using, and says not to let the wording
  drift back.

`docs/manual/part-1-tutorial.md` gained a short paragraph at the end of step 10, where the
caching rule already lives, because the two rules are the same rule.

The first draft of the fix over-claimed in the same way the text it was fixing did. It said
`AiServices`, `@Tool` methods, RAG retrievers and guardrails "take a `ChatModel`, a
`StreamingChatModel`, a `ModerationModel` or a `ChatMemoryProvider` as their input", in three
places. Only the AiService does. `javap` over the aggregate shows guardrails and retrievers
arriving through `inputGuardrails(...)`, `outputGuardrails(...)` and `contentRetriever(...)`
on the same builder, and a retriever's own input is an `EmbeddingModel`, which this library
does not build at all. All three passages now say the accurate thing — they are registered on
an `AiServices`, and a bundle holds what the `AiServices` is built from — and the two
user-facing ones add that RAG needs an `EmbeddingModel` of your own. Caught by reading the
diff before committing, which is the check `CLAUDE.md` asks for.

#### Verified

- `mvn -B clean verify` green with every provider key unset: **137 tests in core**, unchanged,
  because no library module was touched. The only Java changed is `ConsoleChat`.
- Run live, twice, on the machine in this repository's measurement note (AMD Ryzen 7 7840HS,
  Pop!_OS 24.04, Temurin 25), by piping a scripted conversation into `./run-chat.sh`:
  - `CR` (OpenAI `gpt-5.1`, moderated, **no** memory): `/tools`, then "What time is it right
    now?" — one moderation call, then the tool call printed by `now()`, then an answer
    carrying the value the tool returned.
  - `SL` (Anthropic `claude-sonnet-5`, `message-window` memory): a direct turn asking it to
    remember a number, then `/tools`, then a question needing both the number and the time —
    the answer had both, so the `ChatMemory` really is shared across the toggle.
  Five chat calls and one moderation call in total.
- `./run-chat.sh --help` re-read after editing `build/run-example.sh`.
- `build/check-docs.py`: 44 ADRs, 58 tracked markdown files, no problems.
- No ADR: [ADR-0003](../adr/0003-bundle-holds-config-shaped-inputs-only.md) already draws this
  boundary where the work leaves it, and nothing about the scope moved.

---

### P22 — The council asks your question, and Maven's warning stops landing in the output

**Status:** Done 2026-09-01 · **Branch:** `task/p22-council-asks-your-question`

Two complaints from one run of `./run-council.sh`, both about what the terminal showed rather
than about the library: the example asks a question the reader did not choose, and four lines
of JVM warning arrive before it. The first fix went one round further — the question became an
input with a default on Enter, and then the default went away and the single question became a
loop.

#### The questions are now typed, in a loop

`ThreeModelCouncil` had one fixed question in a `private static final String PROMPT`, asked
once. It now reads a question from standard input, puts it to every configured model, and asks
for the next one, until `/exit` — or until standard input ends, which is what makes
`printf '%s\n' "..." /exit | ./run-council.sh` work in a script. An empty line asks again
rather than doing anything.

**There is no default question.** The first version of this had one, on Enter, and the owner
removed it: a question that costs one request per model should be one somebody meant to ask,
and Enter is what a reader presses to see what happens. The example prints how many requests
each question costs — from `registry.names().size()`, so it is right for a configuration that
is not the bundled three.

Standard input rather than a command-line argument, and the reason is in the launcher already:
`exec:java` splits `-Dexec.args` on whitespace, which is why `build/run-example.sh` refuses a
configuration path containing a space. A question does not survive that split either.

The prompt comes **after** the registry is built and the names are printed, so a configuration
that does not parse costs nobody a typed question. Inside one round every model gets the same
question — asking per model would make this a chat, which is what `ConsoleChat` already is.

Two details borrowed from `ConsoleChat`, so the two examples behave the same way: `/exit` is
matched with `equalsIgnoreCase`, and a failing request is **not** caught. A provider error ends
the run with its stack trace here as it does there.

The reader is not closed, on purpose: closing a `BufferedReader` wrapped around `System.in`
closes `System.in` with it. It is opened once, outside the loop.

#### The warning was Maven's, not ours

The block is four lines, three of which name the method:

```
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::objectFieldOffset has been called by
         com.google.common.util.concurrent.AbstractFuture$UnsafeAtomicHelper
         (file:/usr/share/maven/lib/guava.jar)
```

`mvn -pl modelrack4j-examples dependency:tree | grep -c guava` is **0**: Guava is not a
dependency of this project, directly or transitively. The jar is the copy Maven itself ships
(Guava 32.0.1 in Maven 3.8.7 on this machine), and the same warning appears when running
`dependency:tree`, which executes no example code at all. It reaches the example's output
because `exec:java` runs the main class **inside the Maven process**. JDK 24 and later warn on
every `sun.misc.Unsafe` memory-access call, so a newer JDK made a pre-existing call visible.

`build/run-example.sh` now exports
`MAVEN_OPTS=--sun-misc-unsafe-memory-access=allow`, which permits the call without the
warning. Verified: `./run-atomic.sh 2>&1 | grep -c 'sun.misc.Unsafe'` is `0`; the same
`exec:java` run with `MAVEN_OPTS` unset gives `3`.

**The flag is probed, not assumed.** It does not exist before JDK 23, and an unrecognised
launcher option makes the JVM fail to start rather than being ignored:
`-XX:+IgnoreUnrecognizedVMOptions` covers `-XX` options, not launcher ones. This project's
floor is Java 17
([ADR-0019](../adr/0019-target-java-17.md)), so the script runs
`java --sun-misc-unsafe-memory-access=allow -version` first and only exports the flag if that
succeeds. `$JAVA_HOME` is preferred over the `java` on `PATH`, because that is the JVM Maven
will use.

This is a launcher fix, so it applies to all five examples, not only the council.

#### Verified

Run live on the machine in this repository's measurement note (AMD Ryzen 7 7840HS, Pop!_OS
24.04, Temurin 25, Maven 3.8.7):

- `printf '%s\n' "In one short sentence: what is a configuration layer?" /exit | ./run-council.sh`
  — three real requests, three answers to *that* question, the prompt returning afterwards,
  and no warning anywhere in the output.
- The three paths that spend nothing, driven for free by pointing the example at a
  configuration holding a deliberately invalid key: `/exit` on its own leaves without sending
  a request, an empty line asks again, and standard input ending with no `/exit` leaves the
  same way `/exit` does.
- `1 request` rather than `1 requests` on that one-model configuration.
- `./run-council.sh --help` re-read after editing `build/run-example.sh`.

#### No ADR

Nothing here constrains future code. The example's question was never a decision, and the
`MAVEN_OPTS` line changes how a script invokes Maven, not what the library does.
