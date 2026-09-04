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

> **2026-09-03 (P25):** the `api-key = "k"` this paragraph points at is gone.
> `GlmProviderFactory` now requires a GLM key to have the shape its own token builder needs,
> so `GlmProviderFactoryTest` carries conforming keys. The argument about *model* names is
> unaffected — those still pass through as any string.

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

---

### P23 — Housekeeping: key-shaped files cannot be committed, and the guidance is called `AGENTS.md`

**Status:** Done 2026-09-02 · **Branch:** `task/p23-gitignore-keys-and-agents-md` ·
**Produced:** [ADR-0046](../adr/0046-agent-guidance-lives-in-agents-md.md)

Two unrelated pieces of housekeeping, deliberately kept as one item because the owner asked
for them together and neither is large enough to be a task of its own. They share nothing but
the branch.

#### `.gitignore` now covers key material

The gap has a history. On 2026-08-30, while preparing [M6](milestones.md#m6--gpg-signing-and-central-portal-publishing),
the backup commands for the release signing key were run from inside this checkout, so
`chiave-privata.asc` and the revocation certificate landed in the working tree of a **public**
repository as untracked and — the part that mattered — **not ignored**. One `git add -A` would
have staged them. They were moved out, and `git log --all --diff-filter=A` confirmed they had
never reached a git object, but nothing stopped it happening again: `.gitignore` covered
`brainstorm/`, `.env`, `local.conf` and `**/secrets.*`, and nothing key-shaped.

Eleven patterns now do: `*.asc`, `*.gpg`, `*.pgp`, `*.rev`, `*.key`, `*.pem`, `*.p12`, `*.pfx`,
`*.jks`, `*.keystore`, `secring.*`, plus `gpg-backup*/` for the directory the incident actually
created. The list is wider than what this project uses, on purpose — no file of any of those
kinds belongs in this repository, so there is nothing to trade off.

**Checked rather than assumed, in both directions.** `git ls-files` matches **zero** tracked
files against those patterns, so nothing already committed becomes invisible. And recreating
the incident — `touch chiave-privata.asc B9602C49.rev test.p12` — leaves `git status` clean,
with `git check-ignore -v` naming the rule that catches each one.

This closes the loose end [P7](#p7--closing-out-the-outside-review-of-the-public-repository)
left open when it noted that `.gitignore` had no rule of this shape.

#### `CLAUDE.md` is now `AGENTS.md`

The file's name said which tool read it; its contents say nothing tool-specific. Renamed with
`git mv`. **The history follows it only with `--follow`**: because `CLAUDE.md` still exists,
with new content, git records the change as one deletion and one addition rather than as a
rename, so plain `git log AGENTS.md` shows a single commit while `git log --follow AGENTS.md`
walks back through P21, P20 and the rest. Worth knowing before concluding that the file has no
past. `CLAUDE.md` stays behind as a pointer holding no guidance of
its own, because Claude Code loads that name and because seven accepted ADRs cite it — ADR-0015,
ADR-0025, ADR-0026, ADR-0036, ADR-0037, ADR-0039 and ADR-0043 — in bodies that cannot be
edited. [ADR-0046](../adr/0046-agent-guidance-lives-in-agents-md.md) has the reasoning;
[ADR-0037](../adr/0037-claude-md-is-tracked-and-maintained.md)'s status line now records that
its file's name was amended, which is the only part of it that moved.

**Three references were changed and the rest were left alone**, which is the distinction that
took the reading. Inside the file, its title and its opening line named `CLAUDE.md` and now
name `AGENTS.md`; every other self-reference in it says "this file" and needed nothing. In
`docs/tasks/README.md`, one sentence states a *live* rule — that `Task 0.4` and `M3` are cited
from the guidance file — and was updated. Everything else that mentions `CLAUDE.md` across
`docs/tasks/` is a record of something that happened when the file had that name, and rewriting
history to match a rename would make those entries wrong rather than current.

**What this item did not do:** verify how Claude Code treats the `@AGENTS.md` import line. The
pointer works either way, because the sentence above the import tells the reader in plain words
to open the other file, and that is what ADR-0046 relies on. If a later session establishes the
behaviour, it belongs here rather than in the ADR.

#### Verified

- `build/check-docs.py`: clean across 46 ADRs and 61 tracked markdown files.
- `mvn clean install`: green, 170 tests. Nothing here touches code, and that is the point of
  running it.

---

### P24 — `watch(true)` cannot see file-backed layers given through `sources(...)`

**Status:** Done — at least one file layer, the rest ignored; ADR-0050 ·
**Settled:** the argument below was put to the owner on 2026-09-03 and answered

An application that both writes a configuration layer and wants hand edits to keep working
needs `store()` and hot reload at once. The two are configured through builder methods that
exclude each other.

`store()` requires the target layer to be a `WritableConfigSource`, which requires
`sources(...)`. `sources(...)` then makes `watch(true)` throw *"watch(true) watches
configuration files, and this registry has none"* — at the moment when every layer the caller
passed **is** a file (`ConfigSource.ofFile` and `ConfigSource.ofWritableFile`, both
`FileBacked`). The message is accurate about the mechanism and misleading about the situation.

The documented way round is to build the notifier by hand:

```java
.sources(List.of(ConfigSource.ofFile(defaults), runtime))
.notifier(FileChangeNotifier.of(List.of(defaults, runtimeFile), Duration.ofMillis(300)))
```

which works. The cost is that the same paths are now written twice in two shapes with nothing
comparing them: **drop a path from the notifier list and that layer silently stops being
watched.** No error, no log line, and the symptom is the one this library exists to prevent —
edits to a file that never reach the application. The manual's troubleshooting table names the
exception but not this consequence.

**The work, as taken:** `watch(true)` accepts layers from `sources(...)`, watching the
`FileBacked` ones and ignoring the rest, and the exception is kept for the case it was written
for — a registry whose layers are *all* unwatchable.

**The argument against** was that a registry mixing a database row with a file would then be
"watched" in a way that covers only half its layers, and a caller who reads `watch(true)` as
"changes reach me" would be half right — arguably worse than an exception that forces the
question. It was put to the owner and it lost, on a point the entry had missed: refusing the
mixed case does not make the row watchable. It sends the caller to
`FileChangeNotifier.of(...)`, which watches exactly the same half, and adds a second path list
that can silently drift out of step with the first.

#### The decision, and the fact it turned on

The owner's question was whether `watch(true)` is called only when files are present, or
whether that is unpredictable at design time — because if it is predictable, the rule is
simply *at least one file layer, and the rest does not count*.

It is predictable, and statically so. At `build()` the layer list is complete and validated,
and each layer either implements `FileBacked` — `FileConfigSource` and
`WritableFileConfigSource`, what `ConfigSource.ofFile` and `ofWritableFile` return — or it does
not. `ConfigLoader.parse` already makes exactly this test, to choose between `parseFile` and
`parseString`. Nothing about a layer's kind changes after `build()`, so counting files is a
read of the list rather than a forecast. **The rule stands: `watch(true)` requires at least one
file layer; the rest is ignored.**

#### What was found

**The code had been stricter than the ADR it came from.** ADR-0042's Decision section says
`watch(true)` "without file **sources**" fails at `build()` — a statement about layers. The
implementation tested `watchableFiles`, a builder field only `configFiles(...)` fills and
`sources(...)` cleared, which is a statement about which method the caller happened to call.
Nobody had to decide to make the mixed case work: ADR-0042 had already said it. The only
genuinely open question was the one the owner answered.

So ADR-0050 amends ADR-0042 rather than reversing it, and the field is gone: `configFiles(...)`
and `sources(...)` are now exactly equivalent.

**The limit this deliberately keeps.** `FileBacked` is package-private, so an application's own
`ConfigSource` that reads a file cannot declare itself one, and `watch(true)` will not see it.
The way through already exists and is documented — `notifier(FileChangeNotifier.of(...))`.
Making the interface public, or adding a path-shaped method to `ConfigSource`, would put the
filesystem back into the interface ADR-0042 removed it from, and is not taken here.

**The same branch also carries [P31](#p31--a-layer-answers-for-itself-instead-of-being-recognised),**
which removes the `instanceof` this entry's fix had just made the second of two. The owner
asked for it here rather than on a branch of its own, because it finishes the same thought.

**Verification.** `ConfigSourceTest` went from 23 tests to 26: the refusal now also asserts
that the message names the layers, and three cases were added — every layer a file through
`sources(...)`, a mixed registry where the file is watched and the row is asserted *not* to be,
and the case P24 exists for, one registry that stores a writable layer and picks up a hand edit
of another. `mvn clean install` is green at 145 core tests.

PIT on core: 200 mutants, 197 killed, 1 survived, 1 `NO_COVERAGE`, 1 timed out. The two that
are not killed are the two already known — the equivalent `Optional.empty()` in
`LlmRegistry.reload` from P27, and the deliberate cleanup branch in
`WritableFileConfigSource.stage` — so this change added no survivor and no uncovered line.

The end-to-end case was also run outside the test suite, against the real `openai` provider
with an unused key: one registry built from `ofFile` plus `ofWritableFile` with `watch(true)`,
a `store()` that published without firing a listener, then a hand edit of the base file that
arrived as `onReload updated=[SL]`. Both free examples still run — `AtomicSnapshot`, which
uses `configFiles(...)` with `watch(true)`, and `DatabaseSource`, which uses `sources(...)`
with no watching at all.

---

### P25 — A malformed GLM key fails before the call, past the exception guarantee

**Status:** Done — three failure modes, not the one the entry described; ADR-0049

The manual's [exceptions](../manual/part-2-reference.md#exceptions) section tells an application
to catch `dev.langchain4j.exception.LangChain4jException` and says its handling then survives
any provider swap, because all four providers throw beneath it. That claim was checked and it
holds for what a failing **call** throws: `javap` on
`langchain4j-community-zhipu-ai:1.19.0-beta29` confirms `ZhipuAiException extends
LangChain4jException`.

The hole is a failure that happens **before** the call. GLM builds its authorisation token by
splitting the API key on `.` and taking element `[1]`, so a key that is not `id.secret` shaped
throws a bare `java.lang.ArrayIndexOutOfBoundsException` from
`dev.langchain4j.community.model.zhipu.AuthorizationUtils.generateToken`, inside
`ZhipuAiChatModel.doChat`, with nothing in the message about a key. It sails past the catch the
manual recommends, so an application that maps the library's exceptions onto responses reports
a bad credential as an internal fault.

No live GLM call is needed to reproduce it and no funded account either: any key string without
a `.` will do.

**The work, if it is taken**, in the order they are worth doing:

1. `GlmProviderFactory.validate()` rejects a key that is not `id.secret` shaped, at load time.
   This is exactly what that hook is for — a capability check that fails fast, with a message
   naming the block — and it converts an unhandleable runtime crash into the library's own
   `ConfigValidationException`. It is also the only one of the three that this repository
   controls end to end.
2. A sentence in the manual's exceptions section: the `LangChain4jException` guarantee covers
   what a failing call throws, not what a provider throws while assembling credentials.
3. An issue upstream against `langchain4j-community-zhipu-ai`. Lowest leverage, longest loop.

**Open question for the owner:** whether validating a key's *shape* is a capability check or a
credential check. The schema deliberately does not validate `model-name` because upstream's
enums lag the providers, and there is a consistency argument that key shapes rot the same way.
The counter-argument is that this shape is not a catalogue of live values but a format the
provider's own code requires unconditionally, and that the current failure is a JDK exception
with no diagnostic in it.

The owner answered on 2026-09-03: **validate the shape**, on the argument above — a format the
provider's code requires unconditionally is not a catalogue of live values. The upstream issue
(item 3) was not taken.

#### What was found

**The entry described one failure mode and there are three.** All of them happen before any
socket is opened, and none of them is a `LangChain4jException`. Measured by driving a
`ZhipuAiChatModel` at `http://127.0.0.1:1/`, a port nothing listens on, so a key that gets as
far as connecting fails differently from one that does not — `langchain4j-community-zhipu-ai`
`1.19.0-beta29`, Temurin 25.0.3.

| `api-key` | Thrown | From |
|---|---|---|
| `no-dot-in-here-at-all` | `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1` | `AuthorizationUtils.generateToken:60` |
| `id.` | the same — `split("\\.")` drops the empty tail | the same |
| `id..s` | `IllegalArgumentException: Empty key` | `javax.crypto.spec.SecretKeySpec.<init>:108` |
| `id.secret` | `io.jsonwebtoken.security.SignatureException` ← `WeakKeyException`, *48 bits … MUST have a size >= 128 bits* | the JWT library, inside `generateToken:72` |
| `id.0123456789abcde` (15 bytes) | the same, *120 bits* | the same |
| `id.0123456789abcdef` (16 bytes) | **`ConnectException`** | `ZhipuAiClient.chatCompletion:109` |
| `.0123456789abcdef` | `ConnectException` | the same |

**The boundary is exactly 16 bytes**, which is RFC 7518 section 3.2: an HS256 key must be at
least as long as the hash output. The last two rows are what makes "before the call" a
measurement instead of a claim: a key of an acceptable shape gets far enough to open a socket,
so every row above it failed earlier than that.

**The first probe proved nothing, and it took a second run to notice.** Its control case was
`id.secret`, chosen as obviously well-formed. That key has a 6-byte secret, so it failed too —
the run showed five failures and no contrast at all. The mistake is worth recording because the
output looked like a clean result: every row threw, every row named the key handling, and
nothing in it announced that the case which was supposed to succeed had not.

**The most likely real mistake was the one the entry's fix would have missed.** A key with no
dot is a typed-in placeholder. A key with a short secret is a truncated copy-and-paste, and it
keeps its dot, so the check as P25 first described it — reject a key that is not `id.secret`
shaped — would have let it through to the same undiagnosable failure. The owner was asked
again with the measurements in hand, and chose to cover both.

**An empty id half is deliberately left alone.** `.0123456789abcdef` signs and is sent, and the
server answers it with a real `ZhipuAiException`. Refusing it here would be this library
guessing about a credential rather than about a shape the provider's code needs.

#### What changed

- `GlmProviderFactory.validate()` rejects both shapes, in one message that names the block and
  never the key. Byte length, not character count, because that is what the provider signs
  with — a test pins the difference with an eight-character, sixteen-byte secret.
- `ZhipuAiKeyHandlingTest`, a new class, characterises the upstream behaviour rather than this
  library's. It exists so that a future module version that reports these properly makes the
  check here visibly redundant, and ADR-0049 says not to delete it for looking like a test of
  someone else's code.
- Five `api-key` values in `GlmProviderFactoryTest` were `"k"` (three) or
  `"test-key-not-used"` (two), none of which the provider could ever have built a token from.
  They are conforming keys now. The moderation and token-window tests would have kept passing
  either way, because
  `validateCapabilities` runs before `factory.validate` — worth knowing, not worth relying on.
- `GlmProviderIT` needed no change: it reads `ZHIPU_API_KEY` by mandatory substitution, so it
  carries whatever key the environment holds. **Whether a real key satisfies the rule was not
  checked here** — `ZHIPU_API_KEY` is not on a non-interactive shell's environment on this
  machine, and `mvn -Pintegration verify` is what would settle it. The argument that no working
  key is refused does not depend on knowing Zhipu's format: the check rejects only keys the
  provider's own token builder cannot use, so anything it refuses could never have made a
  call.
- The manual gained the sentence item 2 asked for, in the exceptions section, plus a
  troubleshooting row and a line in the GLM provider note.
- `AGENTS.md` said P27 had left all four `validate()` bodies empty. True when written, false
  now, and corrected in this commit — the summary there carries the new boundary rather than
  the new code.

#### Verification, and the defect it found

Run after the pull request was already open, on the development machine — AMD Ryzen 7 7840HS,
Temurin 25.0.3, Maven 3.8.7.

**A review against the Java skill found one real defect, in a message.** The rejection for a
key that does not split said *"it has no '.', so it has no secret part"*. `split("\\.")` drops
trailing empty parts, so `api-key = "."` and `api-key = "..."` return a zero-length array and
land in that same branch — and the message then tells the user their key has no dot when it is
nothing but dots. Confirmed by building a registry for each shape and printing what came out,
not by reading. The clause is now *"it has no secret part"*, which is true of every key that
reaches it, and `keyOfNothingButDotsIsRejected` pins it, including asserting that the old
wording is absent.

Nothing else came out of the review. The method is stateless, holds no resource, dereferences
only components `LlmConfig`'s compact constructor has already proven non-null and non-blank,
and names its one constant. `split("\\.")` takes `String`'s two-character fast path and
compiles no `Pattern`, so precompiling it would buy nothing.

**Mutation testing on core: 199 mutants, 197 killed, 2 minutes 23 seconds.** The two that
survive are the two `AGENTS.md` already describes, unchanged and needing nothing:
`LlmRegistry.reload:339` `Optional.empty()` → `Optional.empty()`, equivalent by construction,
and `WritableFileConfigSource.stage:139` `NO_COVERAGE` on the `discardStaged` call in the
cleanup `finally`, which needs a filesystem that fails between `createTempFile` and
`writeString`. **This says nothing about the code in this item.** `GlmProviderFactory` is in a
provider module, and ADR-0041 forbids running PIT there because each provider module carries a
paid `*IT.java`. The figure for comparison: ADR-0043 recorded 122 s over 153 mutants, and core
has grown by P19, P20 and P29 since.

**Both free examples run.** `./run-atomic.sh` and `./run-database.sh` — the two that send no
request — completed with their expected output; the other three need keys, so only their
`--help` was checked. `mvn clean install` is 188 tests green on this branch, and 189 once main is merged in — P29 added one, and `build/check-docs.py` clean.

---

### P26 — Two documentation gaps

**Status:** Done — both reproduced here; step 7 showed output its own command cannot produce

Neither is a defect in the code; both cost time that the manual could have saved.

**`storeIfUnchanged` compares byte for byte, and a shell capture quietly changes the bytes.**
The comparison being on the text rather than on its meaning is deliberate and right
([ADR-0044](../adr/0044-store-a-layer-back-as-text-validated-before-it-is-stored.md)); the trap
is that the obvious way to read a layer in a shell — `expected=$(cat layer.conf)`, or the same
through a command substitution of any kind — strips the trailing newline, so a perfectly good
edit built on it comes back as `StaleLayerException` and the caller concludes the check is
broken. It takes a byte comparison to see it. A troubleshooting row would pay for itself:
*symptom* — a valid edit is refused as stale; *cause* — the expected text lost a trailing
newline in transit; *fix* — carry the layer's text without reshaping it.

**Whether the tutorial's step 7 output reproduces under `mvn -q … exec:java` is unverified.**
An application run through the exec plugin shares Maven's SLF4J binding, where `-q` can leave
**no** application log line on the terminal — neither the reload messages nor the library's own
`WARN` for a rejected reload.
[Step 7](../manual/part-1-tutorial.md#7-break-the-file-on-purpose) shows that `WARN` arriving
from exactly that command shape, and a reader who does not see it learns the wrong lesson about
a rejected reload. **This has not been observed in this repository**, whose launchers may
already differ, so the first task is to reproduce step 7 here as written and only then decide
whether the tutorial or the launchers need to change. Recording it as a finding before that
check would be recording something this repository has not found.

#### What was found

**Both gaps are real, and the second one is worse than the entry expected.** Everything below
was run on this machine — Temurin 25.0.3, Maven 3.9.16, `exec-maven-plugin` 3.6.3, against the
installed `0.1.0` — driving `ConsoleChat` through a fifo so the same script could type `1`,
delete the `model-name` line, and type `/exit`.

**Step 7 of the tutorial showed a `WARN` line that its own command cannot produce.** The
command the tutorial gives is `mvn -q -pl modelrack4j-examples exec:java …`, and under it the
terminal gets only the example's own bracketed `onReloadFailure` line. The library's
`WARN` never appears. Four runs, same script, only the flags differing:

| Command | Maven's own lines | the library's `WARN` |
|---|---|---|
| `mvn -q … exec:java` — what the tutorial prints | none | **absent** |
| `mvn … exec:java` — no `-q` | fourteen `[INFO]` lines, eight before the example starts | present, with the exception's stack trace |
| `-q -Dorg.slf4j.simpleLogger.defaultLogLevel=warn` | none | **absent** |
| `-q -Dorg.slf4j.simpleLogger.log.io.github.maxtrezzi.modelrack4j.LlmRegistry=warn` | none | present, with the stack trace |

A fifth run checked the page rather than the finding: the command the tutorial now prints,
copied out of it flag for flag, gives the `WARN` and no `[INFO]` line at all.

The mechanism is Maven's, and it is in Maven's own artifact rather than inferred from the
behaviour: `org/apache/maven/cli/logging/impl/Slf4jSimpleConfiguration` in
`maven-embedder-3.9.16.jar` carries the strings `org.slf4j.simpleLogger.defaultLogLevel` and
`error`. `-q` makes Maven set that system property, `exec:java` runs the example inside the
same JVM, and the examples' `slf4j-simple` reads the same property — so the application goes
quiet with Maven. A `-D` for that property on the command line changes nothing, because
Maven sets it after parsing the command line. A *per-logger* level is the way through: Maven
never writes `org.slf4j.simpleLogger.log.<logger>`, so it survives, and `-q` still silences
Maven.

The `WARN` text in the tutorial is genuine `slf4j-simple` output, so it was seen — but under a
command the page does not print, and with the stack trace dropped from the transcript. It was
written in P3 (`65b8a4b`) and had stood since.

**The trailing-newline trap reproduces exactly as the entry described.** Compiled against the
examples' classpath and run outside Maven: a layer file of 171 bytes, `expected=$(cat
layer.conf)` giving 170, `text()` ending in a newline and the capture not, and
`storeIfUnchanged` throwing `StaleLayerException` with `current()` 171 characters against an
`expected` of 170. The file was left untouched, which is the part that makes it look like a
broken check rather than a refused write.

#### What changed

- **The tutorial's step 7** now prints what its own command prints — the listener line alone —
  and then gives the command that shows the `WARN` as well, with the property named and
  explained in one paragraph. The launchers keep `-q`: each example already reports a rejected
  reload through its own listener, and the `WARN` brings a stack trace with it.
- **The reference** gained a paragraph under [logging](../manual/part-2-reference.md#logging),
  a sentence in the `storeIfUnchanged` section about the trailing newline, and two
  troubleshooting rows — one per gap.
- **`LlmRegistry.storeIfUnchanged`'s Javadoc** says the same thing about the newline, because
  a consumer reads that before the manual.

#### Verified

- The four table rows above plus the fifth run of the page's own command — five full runs of
  the tutorial's step 7, each driven through a fifo.
- The newline trap, as a compiled reproduction against the installed `0.1.0`.
- `build/check-docs.py`: clean across 46 ADRs and 61 tracked markdown files.
- `mvn clean install`: green, 170 tests. The only code change is a Javadoc paragraph.

---

### P27 — A review of all seven modules, the examples and the manual

**Status:** Done 2026-09-03 · **Branch:** `task/review-all-modules-and-the-council` ·
**Produced:** [ADR-0047](../adr/0047-redact-the-credential-from-llmconfig-tostring.md),
[ADR-0048](../adr/0048-providers-report-capabilities-core-enforces-them.md)

**That branch carries [P28](#p28--a-failing-model-ends-the-council-round) as well**, and is
named after the work rather than after either item, per the convention in
[the README](README.md). P28 is the one behaviour change this review turned up: it was found
by running the council during the verification below, and it changes the method this entry had
just rewritten, so the two could not be reordered and there was nothing to gain by keeping
them apart.

A code review of every module's `src/main`, both manual parts and the README, run against the
Java 17 profile, with a mutation run on core alongside it. Eight findings: four that would
block a pull request, four smaller. None critical, and none in the concurrency core — which is
the part that was most worth checking and the part that came out cleanest.

**Branched from P26 rather than from `main`**, because P26 was unmerged and unpushed and two
of the findings below land in `docs/manual/part-2-reference.md`, which P26 had just corrected.

#### The mutation run

`mvn -pl modelrack4j-core org.pitest:pitest-maven:mutationCoverage`, before any change:
**194 mutants, 191 killed, 1 timed out, 1 survived, 1 uncovered.** The three that were not
killed each needed a different answer, and only one of them was a defect:

| Result | Where | Answer |
|---|---|---|
| `SURVIVED` | `LlmRegistry.reload` line 339 | **Equivalent mutant.** That line is `return Optional.empty()`, and `EmptyObjectReturnValsMutator` replaced `Optional.empty()` with `Optional.empty()`. No gap, nothing to do. This is the unkillable mutant [D4](open-decisions.md#d4--mutation-testing-in-ci) counted when it ruled PIT out of CI. |
| `TIMED_OUT` | `LlmRegistry$Builder.chooseNotifier` line 760 | **Detected.** Returning `null` starts no notifier, so the watch tests wait out Awaitility's 10 s ceiling, which is longer than PIT's own timeout. Caught, just not as a failure. |
| `NO_COVERAGE` | `WritableFileConfigSource.destination` line 190 | **A real finding, and not the one it looked like** — see the second half of finding 5. |

After the work: **199 mutants, 196 killed, 1 timed out, 1 survived, 1 uncovered.** The
survivor and the timeout are the same two. The uncovered line is a different one — the new
cleanup branch in `stage()`, which needs a filesystem that fails mid-write and so is left
untested deliberately rather than covered by a test that proves nothing.

#### What was found

**1. `LlmConfig.toString()` printed the API key.** The record's `apiKey` component holds the
credential *after* substitution, and a record's generated `toString()` prints every component,
so one `log.info("{}", bundle.config())` in an application would write a real key to a log.
`LlmBundle` carries the config, so it leaked the same way. Nothing in this repository prints
one — every log and print statement across the four modules and five examples was checked —
so this was latent rather than observed. The project already guards the weaker case, telling
implementers not to put a secret in `ConfigSource.id()` because the library prints it; the
component actually holding the credential had no guard. ADR-0047, and `equals` deliberately
left alone.

**2. `ConsoleChat`'s menu could throw out of `main`.** `chooseConfiguration` took
`registry.names()` and then called `registry.get(...)` once per row, each reading the live
configuration. A save removing a name between the two throws `UnknownConfigurationException`
out of the loop — and this is the example whose Javadoc says *"Leave it running and edit the
file"*, on a registry built with `watch(true)`, so the gap is open by invitation. The same
hazard was found and fixed in `chat()` by [P1](#p1--console-chat-example), which did not look
at the menu drawing it. Now one `snapshot()` for the whole menu.

**3. The reference's Logging table was missing a logger, including a `WARN`.** Core has three;
the table listed two. `WritableFileConfigSource` logs *"could not remove the staged file"* at
`WARN` — the level the section singles out as the one that matters — plus two `DEBUG` lines.
The table was written in `65b8a4b` (P3) and was correct then, when core had two loggers.
`8232bf3` (P20) added the third and edited that same file without touching the table.

**4. The README's logging rationale was falsified by P19.** It said two things are reported
*"through the log and nowhere else, because they happen on the watcher thread, where there is
no caller to throw them to."* Since `reload()` became public, a rejection also happens on the
caller's thread and *is* thrown to that caller; *"nowhere else"* was contradicted two lines
below by the same section conceding that `onReloadFailure` listeners are told; and the staged-
file `WARN` from finding 3 is a third thing that really is logged and nowhere else. Three
errors in one sentence, all of them true when written.

**5. A staged file leaked, and the comment explaining a nearby branch named two causes that
cannot happen.** In `stage()`, `createTempFile` succeeded and a failing `writeString` threw
past it, leaving a hidden `.modelrack4j-staged-*.conf` beside the configuration that no caller
could ever remove — the `StagedFile` is never returned on that path. Now cleaned up in a
`finally`.

The second half is the more interesting one, and it is what PIT's `NO_COVERAGE` was really
pointing at. `destination()` caught an `IOException` from `toRealPath()` and explained it as
*"a loop, or a directory on the way this process may not traverse"*. Measured with a probe on
this machine — Pop!\_OS 24.04 (kernel 7.0.11), Temurin 25.0.3:

| Case | `Files.exists` | `toRealPath()` |
|---|---|---|
| self-referential symlink | `false` | throws `FileSystemException` |
| two-link cycle | `false` | throws `FileSystemException` |
| unsearchable parent directory | `false` | throws `AccessDeniedException` |

`Files.exists` follows links, so in all three the guard returned `false` and the `try` was
never entered: the catch was reachable only through a race, and both causes the comment named
were exactly the ones it could not see. The guard now asks with `LinkOption.NOFOLLOW_LINKS`,
under which a looping link answers `true` — measured, not assumed. The branch became reachable
and has a test, and the comment is now true. **The fix was the guard and the comment, not a
test**, which is not what an uncovered line usually means.

**6. `ThreeModelCouncil` did not use `snapshot()`.** The reference says to take one *"per unit
of work — per request, per council round"*, and the example named after the council round
looped `registry.names()` with a `get()` per model. Not a live bug: that registry sets no
`watch(true)` and never reloads, so nothing can land mid-round. But it is the loop people copy,
and `AtomicSnapshot` exists to show that a torn council pair is a correctness bug. Fixed, with
the class Javadoc rewritten to say why a snapshot per round is not the caching trap.

**7. The moderation capability rule was restated in three provider modules.** Three identical
`validate()` bodies differing only in a hardcoded provider name — one each class already held
as an unused `PROVIDER_ID` — while core owned the equivalent token-estimation rule and the SPI
Javadoc said capability rules "must not be restated here". ADR-0048: a provider reports,
core enforces. `FakeProviderFactory` had already invented `supportsModeration()` for its own
use, which is corroboration the shape was right rather than a preference.

**8. The threading section never mentioned stores.** `LlmRegistry`'s own `@implNote` says a
store takes the reload lock and holds it across the layer's write; the manual's *Threading and
lifecycle* section covered reloads, listeners and `close()` and said nothing about it, so
nobody implementing a slow database-backed `write` would learn from the manual that it blocks
reloads.

#### A miscount in the review itself

The first write-up closed with *"nine findings … four 🟠, five 🟡"* and listed eight. Four plus
four. The rule in `AGENTS.md` is to count the list you just wrote before naming its size, and
it was broken in the sentence summarising a review whose own finding 4 is about a stale count.
Recorded rather than quietly corrected, because [P14](#p14--a-coherence-pass-over-the-tracked-documentation)
and [P15](#p15--a-second-coherence-pass-and-what-the-first-one-missed) are the same failure and
this is a third instance.

#### Not done, and why

- **The `stage()` cleanup branch has no test.** Forcing `Files.writeString` to fail after
  `createTempFile` has succeeded needs a filesystem that fails mid-write. PIT reports the line
  as uncovered and that report is honest; a test that reached it by another route would be
  testing something else.
- **`ProviderSwap`, `ConsoleChat` and `ThreeModelCouncil` were not run.** All three spend real
  money. The two free examples were run and matched the manual exactly.
- **The exception-type question in [D6](open-decisions.md#d6--cannot-store-is-not-your-configuration-is-invalid)
  was not touched**, although finding 5 sits in the code it is about.

#### Verified

- `mvn clean install`: green. **177 tests**, up from 170.
- PIT on core, before and after, with every non-killed mutant accounted for above.
- `build/check-docs.py`: clean across 48 ADRs and 63 tracked markdown files. It caught one
  broken link in ADR-0047 on the way — a guessed filename for ADR-0006.
- `./run-atomic.sh` and `./run-database.sh`, both matching what the manual says they print.
  AtomicSnapshot reported one torn `get()` pair against zero torn snapshots in ~117 M samples.

---

### P28 — A failing model ends the council round

**Status:** Done 2026-09-03 · **Branch:** `task/review-all-modules-and-the-council`, shared with
[P27](#p27--a-review-of-all-seven-modules-the-examples-and-the-manual)

Found by running `ThreeModelCouncil` during P27's verification, with deliberately invalid keys.
**It is a separate entry but not separate work.** It is none of P27's eight findings — P27
reviewed, this changes behaviour — which is why it is recorded here under its own number rather
than appended to that list. It shares P27's branch because it rewrites the method P27 had just
rewritten: two branches touching one method would have conflicted on whichever merged second,
for no benefit.

`askEveryModel` called `bundle.chatModel().chat(question)` with nothing around it, so the first
model whose request failed threw out of the loop, out of `main`, and ended the session. The
answers already printed above it went with the session. An expired key on one of three
providers therefore cost all three answers, and the question had already been paid for on
whichever models answered before the failure.

**The other two examples already did the right thing**, which is what made this an
inconsistency rather than a choice: `ConsoleChat` catches `RuntimeException` on both answering
paths and `ProviderSwap` catches it in `ask()`, each reporting the failure and carrying on.
The council — the one example where a single failure costs the most, because the other members
have already been billed — was the one that did not.

#### Built

A `try`/`catch` around the request only, so a failure is reported against the model that
produced it and the loop moves to the next member.

**The exception type is printed, not just its message.** This is the one example where several
providers answer the same question, and the reference's
[Exceptions](../manual/part-2-reference.md#exceptions) section records that the same real
condition arrives as a different type per provider. The council now shows that rather than
asserting it — the run below has `AuthenticationException` from both providers carrying
completely different payloads.

**A partial round says so.** Catching the failure introduced a worse hazard than the crash: a
council is a comparison, and a comparison quietly missing one of its terms reads like a
complete answer. A tally after the loop names how many of the members answered.

#### What the run found in the fix itself

The tally had two wordings' worth of work in it and the first draft only wrote one. With two
dead keys it printed:

```
!! incomplete round: 0 of 2 models answered. Compare them knowing one is missing.
```

Two things wrong in one line — *one* is missing when two were, and *compare them* refers to
nothing when nothing answered. Neither is visible by reading the code, because the sentence is
correct for the case its author had in mind. There are two cases and they need two sentences:

```
!! no answers: all 2 models failed.
```

#### Verified

- Driven with two invalid keys, both before and after the wording fix. Both providers failed,
  both failures printed with their type, the loop continued to the next member and then to the
  next question, and `/exit` left cleanly. A 401 is refused before billing, so this cost
  nothing.
- **The partial case — some members answering, some failing — was not run**, because it needs
  at least one working key and a real request. The all-failed branch and the tally's
  `answered == 0` split were exercised; the other branch of that ternary was read, not run.
- `mvn clean install`: green, 177 tests. No test covers this: the examples module has no test
  scope and no offline provider to fail on demand.
- `build/check-docs.py`: clean across 48 ADRs and 63 tracked markdown files.

**Carried with P27 in one commit, and replanted once P26 landed.** P26 merged as `fb22b4c`
(#46) and this branch was rebased with `git rebase --onto origin/main 8bea5f1` — the recipe
P27 had verified against a simulated squash rather than assumed, used twice for real.

**The first attempt to merge P26 conflicted, and the reason is worth keeping.** A local `main`
that had not been fetched was two commits behind: `b131db2` was already on the remote as the
squash `5ae070a` (#45), so the P26 branch was re-proposing work `main` already had and GitHub
answered `CONFLICTING`. The same `--onto` form fixed it — dropping the commit whose content had
landed under another SHA. Under a squash-merge workflow a branch is stale the moment its parent
merges, and the local ref does not say so. **Fetch before reading `main`'s position**, and treat
a conflict on a branch that never diverged as the signal that this has happened.

---

### P29 — A global check, and the eight things it found

**Status:** Done — 1 defect in core, 7 stale or wrong sentences; no ADR

The owner asked for a full pass over a tree that three sessions had just moved: build, tests,
mutation testing, a Java review and a re-verification of the documents and the examples.
Everything mechanical was already green — 177 tests, `check-docs.py` clean across 48 ADRs and
63 markdown files, and PIT returning [P27](#p27--a-review-of-all-seven-modules-the-examples-and-the-manual)'s
survivor and uncovered line and no others. What the pass was actually for is below.

No ADR. Nothing here settles a new question: finding 1 applies an existing rule to the one
call site that had escaped it, and the other seven are sentences that stopped being true.

#### The starting state, measured rather than assumed

| Check | Result |
|---|---|
| `mvn clean install` | green, 8 modules |
| Tests | 177, no failures, no errors, none skipped |
| `build/check-docs.py` | clean, 48 ADRs and 63 tracked markdown files |
| PIT on core | 199 mutants, 1 `SURVIVED`, 1 `NO_COVERAGE` — the same two P27 signed off |
| `./run-atomic.sh` | 1 torn pair through `get()`, 0 through `snapshot()` |
| `./run-database.sh` | all six steps as documented |
| Tutorial step 7 | the printed command run, its output compared line by line |
| Council, two dead keys | round survives, tally correct |

The PIT run is worth a note. It reports `SURVIVED` 1 and `NO_COVERAGE` 1, as P27 recorded —
but the split between `KILLED`, `TIMED_OUT` and `RUN_ERROR` moved between the two runs
(P27: 196 killed, 1 timed out; here: 176 killed, 19 run errors, 2 timed out, which PIT totals
as 197 killed). Those three categories all mean *detected*, and which one a mutant lands in
depends on timing. **Compare the survivor and uncovered lists between runs, not the killed
count** — the count is not stable on a machine doing anything else.

#### What was found

**1. A `null` where the SPI says `Optional` was an NPE, at one call site of three.**
`SnapshotLoader.requireProduced` checks `produced == null` before `isEmpty()`, and
`createStreamingChatModel` and `createModerationModel` both go through it.
`createTokenCountEstimator` did not — it had its own `orElseThrow`, so a factory returning
`null` there failed with:

```
java.lang.NullPointerException: Cannot invoke "java.util.Optional.orElseThrow(...)" because
the return value of "ProviderFactory.createTokenCountEstimator(LlmConfig)" is null
```

Established with a throwaway probe before anything was changed, then re-established as a
failing test. This is the rule the suite already states out loud: `FakeNullProviderFactory`'s
own Javadoc says a misbehaving factory "must surface as a configuration error naming the
provider, not as a bare `NullPointerException`". One call site did not honour it.

The four providers in this repository all return an `Optional`, so nothing here could reach
it — the SPI is a public extension point, which is what makes a third-party factory the
realistic case rather than a hypothetical one. Routed through `requireProduced`, which also
retires a bespoke message: *"supplied no token count estimator"* becomes *"produced no token
count estimator"*, matching the other two. `FakeNullOptionalProviderFactory` is new, and
returns `null` from all three optional methods — `FakeNullProviderFactory` could not cover
them, because its `null` chat model fails first.

**2. `README.md` said `ProviderFactory` is "seven methods". It is eight.** P27 added
`supportsModeration()` and did not move the count. Counted with `javap` on the built
artifact, not by eye. The "three of which return `Optional.empty()`" half was and stays
correct.

This one has a record in this file already: [P16](#p16--a-third-coherence-pass-and-the-surface-the-first-two-searched-past)
re-verified that exact sentence on 2026-08-28 and wrote "7 and 3" in its clean table. That
entry is not wrong — it is dated, which is what the convention asks for, and the dating is
what makes it readable now as *true then*. **A dated audit records a measurement; it does not
protect the sentence afterwards.**

**3. The reference said three of the four factories have an empty `validate()`. All four do.**
Read all four bodies. Three were emptied by P27 and OpenAI's was already empty, which is how
"three were emptied" turned into "three are empty" — a sentence that sends a reader looking
for the fourth provider's surviving rule. `AGENTS.md` had it right ("leaving all four empty")
while the user-facing document did not.

**4. `AGENTS.md`'s own mutation-testing paragraph contradicted the entry it summarises.**
Two errors in the passage P27 added:

- *"the fix was the guard and the comment, not a test"* — a test was added in that same
  commit, `ConfigStoreTest.write_throughALinkThatCannotBeResolved_writesThePathItself`, and it
  is what covers the branch. P27's commit message says so ("fixed with `NOFOLLOW_LINKS` and a
  test"); only the summary in `AGENTS.md` says otherwise.
- It points whoever runs PIT next at `WritableFileConfigSource.destination()`. P27 *fixed*
  that line. Today's `NO_COVERAGE` is `stage()`, a different method — as P27's own table in
  this file states correctly.

So a session reading the guidance would have gone looking for an uncovered line that is
covered, and taken the wrong lesson about what closed it. **`docs/tasks/` wins on what was
found, and this is the shape of the failure the split exists to prevent**: the durable record
stayed right and the summary of it drifted, in the same commit that wrote both. The paragraph
now sends the reader to `target/pit-reports/` for what is uncovered *today*.

**5. Two `.java` files quote the two-per-million figure with no machine.**
`LlmSnapshot`'s class Javadoc and `AtomicSnapshot`'s. ADR-0038, `README.md` and the reference
all name the machine, after the owner ruled on 2026-09-01 that a figure's conditions may be
completed in place. The rule is that a hardware-dependent number without its hardware is not
worth quoting, and it does not stop at markdown.

This is [P16](#p16--a-third-coherence-pass-and-the-surface-the-first-two-searched-past)'s
lesson arriving a second time by the same route: a pass scoped to *documentation* does not
open `src/main`, and the class the ADR is about is exactly where the claim gets restated.

**6. The reference's logging table said the WARN means a store finished.** It can also fire on
a store that was *rejected*. `discardStaged` has three call sites, and two are failure paths
only: `write()`'s `finally` when the commit fails, and `stage()`'s `finally` when the staged
file is never handed over. The third, `StagedFileWrite.discard()`, is reached from
`LlmRegistry`'s own `finally` and runs whether the store was applied or refused — which is the
one the WARN most often comes from. The row now says the store *ended*, and that what changed
is what the store returned or threw.

**7. `AtomicSnapshot` claimed to be "the one example that runs anywhere, at no cost". There are
two.** [P19](#p19--configuration-sources-and-a-reload-the-application-can-ask-for) added
`DatabaseSource`, which sends no request and uses literal keys, and
[P18](#p18--the-distance-between-arriving-and-running-something)'s launcher was updated to say
so — `run-example.sh` names both. The Javadoc of the example itself was not.

**Found only by reading the whole file after editing it** for finding 5. The two sit in the
same class Javadoc, twenty-one lines apart — the machine attribution at line 51, this claim at
line 72 — so a diff of the first shows nothing of the second. This is the P19 rule doing its
job: the sentence a change makes stale is never in that change's diff. The first draft of this
paragraph said "four lines above it", which is what an unmeasured number is worth.

**8. `AGENTS.md` said the open-decisions file "is currently a record rather than a queue".**
It has been a queue since `5ae070a` (#45) added
[D5](open-decisions.md#d5--a-version-token-for-optimistic-concurrency) and
[D6](open-decisions.md#d6--cannot-store-is-not-your-configuration-is-invalid), both
`Needs decision` and both still open. A session that trusted the sentence would have taken
"D1–D4 are all settled" as the whole list and never opened the file — and the two questions
waiting there are about the public API of `store()`, which is exactly the kind of thing a
session should not answer on its own.

Found while writing the board row for this entry, not by any check. It is the second finding
in `AGENTS.md` in one pass, and the two have the same shape: a summary of a tracked file,
written correct, left behind when the file moved. Both now tell the reader to go and read the
file instead of trusting the summary, which is the only version of that sentence that cannot
go stale.

#### Verified

- `mvn clean install`: green, **178 tests**, up from 177 — one new method in `LlmRegistryTest`
  covering all three optional capabilities, plus the new fake. The first draft of this line
  said 181, counting the three assertions inside that method as three tests; the count came
  from `mvn`, and the sentence had been written before it.
- The new test was **run against the unfixed code first** and failed with the NPE above, so it
  tests the fix rather than accompanying it.
- PIT re-run on core after the change: **198 mutants, 195 killed, 1 timed out, 1 survived,
  1 uncovered.** One fewer mutant than before, because the bespoke `orElseThrow` lambda is
  gone and `requireProduced` was already covered. The survivor and the uncovered line are the
  same two P27 signed off — `LlmRegistry.reload` line 339 and `WritableFileConfigSource.stage`
  line 139. No new gap.
- `build/check-docs.py`: clean, 48 ADRs and 63 tracked markdown files.
- `./run-atomic.sh` and `./run-database.sh` re-run after the edits.
- Tutorial step 7 driven end to end: config written, `model-name` deleted mid-run, the `WARN`
  arrived through the flag the page prints, and `/menu` still showed the deleted model name.
  The page matches what the command produces.
- `ThreeModelCouncil` and `ConsoleChat` driven with dummy keys, which a 401 refuses before
  billing. The council's **partial** round still needs a working key and was not run — the same
  gap P28 recorded.

---

### P30 — `ConfigAccessException`: the implementation of D6

**Status:** Done — five throw sites moved, and a sixth the plan had not found; ADR-0053 ·
**Settled by:** [D6](open-decisions.md#d6--cannot-store-is-not-your-configuration-is-invalid)

The decision is D6's and the reasoning is in
[ADR-0053](../adr/0053-a-separate-exception-for-a-layer-that-cannot-be-reached.md). This entry
records what building it found.

#### What was found

**1. Moving the five planned sites did not change what a missing config file does.** The plan
listed two read sites in `FileConfigSource.read` and three write sites in
`WritableFileConfigSource`, and all five moved cleanly. Then a new test — a missing layer at
`build()` — still caught `ConfigValidationException`, saying *"could not be parsed"*.

The reason is ADR-0042: a file layer is parsed with `parseFile`, not by reading its text, so
`FileConfigSource.text()` is never called during a load. The load path for a file layer runs
through `ConfigLoader`, which wrapped every `ConfigException` as a parse failure. So the
commonest read failure in the library — the file is not there — was reported as a problem with
text that had never been read.

`ConfigLoader.load` now catches `ConfigException.IO` before `ConfigException`. Probed against
`config-1.4.9` before writing the catch, rather than assumed: a missing file is
`com.typesafe.config.ConfigException$IO`, an unreadable file is the same, and a syntax error is
`ConfigException$Parse`. The two cases separate exactly on that boundary.

This is the second time in two days that a decision's plan was right about the code it had read
and blind to a path it had not. The plan was written from a grep for `ConfigValidationException`;
what it could not see was which of those sites the loader actually reaches.

**2. The example needed nothing.** `DatabaseSource` has two `catch (ConfigValidationException)`
blocks, and the plan said to check whether each should also catch the new type. Both surround a
deliberately invalid provider name, so both are still exactly right. Its in-memory `Row` never
fails as a medium.

**3. Two javadoc contracts named the wrong type after the move**, and a grep for
`ConfigValidationException` over every `.java` and `.md` file is what found them:
`SnapshotLoader.load` (both overloads) and `StagedWrite.commit` still said an unreadable layer
or a failed store was a validation failure. Reading the diff would not have shown either —
neither file has a throw site, only the `@throws` line above a method whose behaviour changed
underneath it.

**4. A sentence unrelated to D6 was false, found in the same grep.** The reference said *"All
four factories in this repository now have an empty `validate()`"*. P25 (`d0d0ee6`) gave GLM's
a real body two commits earlier, and `AGENTS.md` already said three of four. Corrected here
rather than left for a later pass, and checked by reading all four bodies, not by trusting
`AGENTS.md`.

**5. A test fixture was carrying the old contract.** `ConfigStoreTest.MemoryRow` threw
`ConfigValidationException("the database is unreachable")` from `write`, which is the failure
the new type exists for. It now throws `ConfigAccessException`, so the suite demonstrates the
contract `WritableConfigSource.write` documents instead of contradicting it.

#### Verification

Core went from 142 tests to 145, and `ConfigStoreTest` from 46 to 47. Four assertions were
updated to the new type — a missing file in `LayeredResolutionTest`, an unreadable source and a
failed move in the two store tests, and both rollback cases — and four cases are new:

- a store onto a read-only directory: `ConfigAccessException` from `stage()`, **before** the new
  text is validated, with the previous configuration live and no staged file left behind;
- a missing layer at `build()`;
- a layer deleted between `build()` and `reload()`: the throw, the `ReloadFailure.cause`, and
  the previous snapshot still serving;
- and, in three of those, an explicit `isNotInstanceOf(ConfigValidationException.class)`, so
  turning one type into a subclass of the other fails loudly rather than silently restoring the
  ambiguity.

`mvn clean install` green. While this branch waited, `build/check-docs.py` reported
`ADR numbering gaps: [50, 51, 52]` on it, which was never a defect: 0050 and 0051 belonged to
P24's branch and 0052 to D5's, both unpushed at the time. The gap closed when they merged —
[#50](https://github.com/maxtrezzi/modelrack4j/pull/50) and
[#51](https://github.com/maxtrezzi/modelrack4j/pull/51) — which is also the merge order this
branch was rebased into: P24, then D5, then this.

---

### P31 — A layer answers for itself, instead of being recognised

**Status:** Done — two type tests became one, at a boundary; ADR-0051 ·
**Branch:** carried by P24's branch, at the owner's request — it finishes the same thought

#### Why it came up

P24 fixed `chooseNotifier` by giving it an `instanceof FileBacked`. That was the right fix for
the defect and it made the codebase's second copy of the same test — `ConfigLoader.parse` has
had one since ADR-0042. The owner read the hierarchy afterwards, said `instanceof` is a design
smell and that this one was the ugly kind, and asked for alternatives.

#### What was rejected, and why it is worth recording

The first instinct — a public `MonitorableConfigSource`, so a layer declares that it can be
watched — is the idea ADR-0042 already refused **twice**, in two forms: an address on the
interface (`Optional<Path>`, then `Optional<URI>`) puts the filesystem back where that ADR had
just removed it from, and a notifier per source undoes ADR-0013's single watch service over the
deduplicated parent directories. Neither argument has aged.

Full polymorphism on `ConfigSource` — `parse`, `watchTarget`, `stage` on the public interface —
removes all three tests and takes the SPI from two methods to five, with
`com.typesafe.config.ConfigParseOptions` in it, so every application-written source would have
to implement HOCON parsing. On a published artifact that is the worse trade.

And Java 17 does not sell the elegant version: a `sealed` type plus an exhaustive pattern
`switch` is Java 21. The project already owns that scar — `MemoryConfig` is `sealed` and
`SnapshotLoader` still ends its chain with a throwing default, because nothing checks it.

#### What was done

The repetition is removable without any of that: ask once, at the boundary every layer already
passes through, and keep the answer. An internal `sealed Layer` (`FileLayer`, `TextLayer`) is
built in `Builder.build()`, and `ConfigLoader`, `SnapshotLoader` and `LlmRegistry` carry
`List<Layer>` and call methods. `FileBacked` is now `sealed` and read in exactly one place.

**Two of the three `instanceof` became one.** `StagedWrite.prepare` keeps its own, deliberately:
it asks whether a *writable* target is a file, which is the other axis, and folding it in would
need a third `Layer` variant that exists only to throw for a read-only file layer. An
unreachable branch is worse than a localised factory, and PIT would have reported it as an
uncovered line for the life of the project.

**The public API does not change**, so there is no CHANGELOG entry. That is deliberate, not an
omission.

#### Verification

`mvn clean install` green, core still at 145 tests — no test changed, which is the useful
signal: the behaviour is identical and the existing suite already covered every path through
the new types.

PIT on core: **205 mutants, 202 killed**, one survived and one `NO_COVERAGE`. Both are the two
already known — the equivalent `Optional.empty()` in `LlmRegistry.reload` and the deliberate
cleanup branch in `WritableFileConfigSource.stage`. `Layer`, `FileLayer` and `TextLayer`
contribute **9 mutants, all killed**, so the new code arrived covered by the existing suite.

The total moved from 200 to 205, which is **not** the number the new types added: the refactor
also deleted mutable code where the two `instanceof` used to be. An earlier draft of this
paragraph took the difference of the totals for the count of new mutants and said five. It is
nine, counted per class in `mutations.xml`.

**A mistake worth recording, because it cost a wrong report first.**
`org.pitest:pitest-maven:mutationCoverage` does **not** compile: it mutates whatever
`target/classes` already holds. The first run on this branch was made after checking out two
other branches and running `mvn test` on one of them, so it silently measured *that* branch's
code — 198 mutants, no `Layer` at all, and a `ConfigAccessException.class` from a branch this
one does not contain. Nothing in PIT's output says which source tree it read. Build first, then
run it, and check that a class you expect is in the report.

**The count, measured rather than asserted.** `grep -rn instanceof --include=*.java */src/main`,
with comment lines dropped, gives **nine** expressions in production code. Two of them are the
subject of this entry — `Layer.of` and `StagedWrite.prepare` — and before the change those two
were three, spread over `ConfigLoader.parse`, `Builder.chooseNotifier` and `StagedWrite.prepare`.
The other seven are unrelated and untouched: two in `MemoryConfig`, four in `SnapshotLoader`
(one `ConfigObject` cast and three over `MemoryConfig`'s sealed variants), and one in
`ConfigWatcher` over a `WatchEvent` context. So the number that moved is three to two, and the
difference that matters is that neither survivor sits at a use site.

---

### P32 — The recipe D5's argument rests on

**Status:** Done — the reference now carries the conditional-write recipe ·
**Raised by:** [D5](open-decisions.md#d5--a-version-token-for-optimistic-concurrency)

D5 decided that the library gains no version token, and the argument for that was that an
application behind an HTTP `PUT` can build one itself in a few lines. That argument obliges the
project to write those lines somewhere a user reads. It was only in
[ADR-0052](../adr/0052-no-version-token-the-expected-text-is-the-token.md), which is not
user-facing documentation (ADR-0039).

#### What was missing

Checked before writing anything, and all three were about writers inside one process:

- `docs/manual/part-2-reference.md`, *More than one writer* — the retry loop over
  `text()` / `storeIfUnchanged` / `StaleLayerException.current()`, plus the trailing-newline
  trap. Correct, and written for two threads.
- `README.md` — one sentence, that `storeIfUnchanged` refuses rather than erasing somebody
  else's change.
- The javadoc of `storeIfUnchanged` — the re-read under the lock and its I/O cost, with no
  mention of a token or of a remote caller.

So nothing told a reader how to join `If-Match` to `expected`, which is the bridge the decision
stands on. ADR-0052's Consequences recorded no follow-up either, so the obligation was written
down nowhere.

#### What was added

A subsection in *More than one writer*: the `GET` that serves an `ETag` over `text()`, the
`PUT` that compares it and answers `412`, and the `storeIfUnchanged` that follows. Plus a
sentence in the javadoc of `storeIfUnchanged` saying why there is no token and pointing at the
manual.

**The paragraph that carries the teaching is about the `catch`, not the `if`.** A reader who
sees both a header comparison and a `StaleLayerException` handler will take one of them for
redundancy and delete it. The header compares against a read that is already in the past;
`storeIfUnchanged` re-reads inside the lock. Only the second closes the gap between the check
and the write.

#### Verification

The recipe was run before it was printed, not sketched — the P18 rule. A probe built a registry
over `ofFile` plus `ofWritableFile`, served an `ETag`, and then took both paths: the `PUT` whose
header matches and stores, and the `PUT` whose header **also** matches and is still refused,
because another writer stored between the two lines. Output:

    PUT  header check: true   (passes: nothing has changed yet)
         StaleLayerException on layer user.conf -> answer 412
         current() holds the winner: SH=o3-mini
         registry still holds SH=o3-mini

That second case is the one the paragraph describes, and it is the reason the `catch` stays.

No code changed beyond the javadoc, so there is no CHANGELOG entry.

---

### P33 — The messages D6 leaves a user, and where they are looked up

**Status:** Done — three troubleshooting rows, and a message that named no cause ·
**Raised by:** [D6](open-decisions.md#d6--cannot-store-is-not-your-configuration-is-invalid) ·
**Branch:** carried by D6's branch

[P30](#p30--configaccessexception-the-implementation-of-d6) gave the library a second exception
type. This entry is what that owed a reader: the messages it produces, in the table where a
reader looks a message up.

#### What was missing

The Troubleshooting table in `docs/manual/part-2-reference.md` had **no row for a layer that
cannot be read or written** — not before D6 and not after it. Every other failure the library
can produce has one. A user whose configuration file is missing, or whose store meets a
read-only directory, found the type described in the Exceptions section and nothing in the
table they actually consult when they hold a message.

Checked and found needing nothing: the tutorial, which shows four failures and all four are
about text; the examples, where only `DatabaseSource` catches `ConfigValidationException` and
both of its catches surround a deliberately invalid provider name; and `README.md`, which names
no exception type at all.

#### The defect the probe found

Running the failures to capture their real text — rather than quoting them from the source —
showed one that says nothing:

    Cannot write the configuration beside /…/locked/app.conf: /…/locked/.modelrack4j-staged-3961….conf

`e.getMessage()` on an `IOException` over a path is usually **just that path again**, and here
that path is the staged temporary file, which the user never asked for and cannot act on. The
message named a random hidden filename and no cause. All three write and read sites had the
same shape. They now interpolate `e` rather than `e.getMessage()`:

    Cannot write the configuration beside /…/locked/app.conf: java.nio.file.AccessDeniedException: /…/locked/.modelrack4j-staged-3961….conf

**This was only visible by running it.** The source reads `+ e.getMessage()`, which looks
correct, and no test asserted on the tail of the message.

#### A second thing the probe settled

The message a user meets at `build()` is **not** the one in `FileConfigSource`. A file layer is
parsed with `parseFile` and never through `text()`, so a missing file surfaces from
`ConfigLoader` as `Configuration source … cannot be read`, while
`Configuration file does not exist or is not readable` appears only when the application calls
`text()` itself. The table says both, and which is which — writing only the second would have
sent a reader looking for a string the library never printed to them.

#### What was added

Three rows: the unreadable layer, the store that cannot write, and the migration symptom —
something escaping a `catch (ConfigValidationException)` that used to catch it, with the reason
the split exists.

#### Verification

`mvn clean install` green, core at 148 tests — unchanged by this item, which adds none: the
message text is not asserted anywhere, which is exactly why the defect survived.
`build/check-docs.py` reported only the two expected cross-branch items while this branch
waited on P24's and D5's ADR numbers, and is clean now that both have merged.

#### Checked again before merging, 2026-09-04

Four things were run over the whole branch, on the machine this repository records:
AMD Ryzen 7 7840HS, Temurin 25.0.3.

**Mutation testing on core: 205 mutants, 202 killed, 1 timed out, 1 survived, 1 uncovered,
2 minutes 50 seconds.** The same three that are not killed as
[P31](#p31--a-layer-answers-for-itself-instead-of-being-recognised) recorded, at the line
numbers this branch moved them to: the equivalent `Optional.empty()` in `LlmRegistry.reload`
(341), the timeout in `Builder.chooseNotifier` (798), and the deliberate cleanup branch in
`WritableFileConfigSource.stage` (142). `ConfigAccessException` produces no mutants at all —
it declares constructors and no logic. The report was checked for a class this branch wrote
before its numbers were believed, which is [P31](#p31--a-layer-answers-for-itself-instead-of-being-recognised)'s
lesson: `ConfigLoader.load`'s mutants sit at lines 87, 88 and 96, where this branch's catch
block put them, and not at main's.

**All five examples were run.** `AtomicSnapshot` and `DatabaseSource` cost nothing;
`ProviderSwap`, `ConsoleChat` and `ThreeModelCouncil` made real calls. The council answered
from all three configured models in one round, with moderation on `CR` alone, streaming on
`SH` alone and memory on `SL` alone — the capability matrix, observed rather than asserted.
`ConsoleChat`'s `/tools` was driven to the end: the `@Tool` method ran
(`[tool called: now() -> …]`) and the model used its result, which is
[P21](#p21--what-the-library-leaves-available-said-out-loud)'s claim about `AiServices`
demonstrated rather than described.

**`mvn -Pintegration verify`: four tests, four providers, all passing.** So the four
configured identifiers still exist upstream — `gpt-5-mini`, `claude-sonnet-4-6`,
`gemini-3.6-flash`, `glm-5.3` — and `examples.conf`'s `gpt-5.1` and `claude-sonnet-5` answered
through the examples. **This also settles what
[P25](#p25--a-malformed-glm-key-fails-before-the-call-past-the-exception-guarantee) left open:**
that entry could not check whether a real GLM key satisfies the `id.secret` rule with a secret
of at least 16 bytes, because `ZHIPU_API_KEY` was not on the shell it ran from. `GlmProviderIT`
passed with the real key, so the check refuses no working credential.

**The documentation was checked by running it, not by reading it.** The four failures the
tutorial prints in sections 6 and 7 were reproduced from a probe and match the printed text —
moderation on Anthropic, token-window on Anthropic, an unset mandatory substitution, and a
block with `model-name` deleted, all `ConfigValidationException`. A separate probe deleted a
watched file and called `reload()`: `ConfigAccessException` naming the path, the `WARN` the
README promises, and the previous configuration still live afterwards. One thing to know about
that message — Typesafe's `ConfigException.IO.getMessage()` already begins with the path, so
interpolating the cause puts the path in three times. It is verbose rather than wrong, and
trimming an upstream prefix is a decision rather than a fix.

**One defect found, in the reference's exceptions table.** *"Those five are the library's
own"* — four are. `java.io.UncheckedIOException` is the JDK's, and it has been in that table
since before this branch, which raised the count from four to five and carried the wording
with it. Corrected here: the sentence now says the library throws five and owns four.

---

### P34 — LangChain4j 1.20.0, and the jar the aggregate started bringing

**Status:** Done 2026-09-04 · **Branch:** `task/p34-langchain4j-1-20-0` · no ADR

LangChain4j `1.20.0` was published on 2026-09-04, the community train with it as
`1.20.0-beta30`. The owner asked what in it concerns this project. Most of it does not — the
release is largely MCP, agentic and A2A work, embedding stores, RAG and provider-specific
features this library does not configure. Two things do, and neither is in the release notes.

This is the one-line BOM bump [ADR-0018](../adr/0018-manage-langchain4j-versions-via-bom.md)
was designed to make routine. It needed a second line, and the reason is worth writing down.

#### 1. The aggregate stopped being free

[ADR-0020](../adr/0020-core-depends-on-langchain4j-aggregate.md) lets core take the
`langchain4j` aggregate for `ChatMemoryProvider` alone, and the argument rests on a
measurement recorded in [M0](milestones.md#m0--skeleton-and-ci): the aggregate
declares six dependencies and every one is already present or excluded, so it costs one jar
and adds nothing transitively. M0 says in the same paragraph to re-run the command on every
bump, because a dependency added upstream would land there silently. That is what happened.

| At | Aggregate declares | Core's compile scope | Aggregate jar |
|---|---|---|---|
| `1.19.0` | six | eight artifacts | 317 KB |
| `1.20.0` | seven | **nine** artifacts | 389 KB |

The new one is `io.smallrye.reactive:mutiny-zero:1.3.1`, 58 KB. It arrived with the
release's headline feature: an AI Service method may now return `CompletableFuture<T>` or
`Flow.Publisher<…>` and run without holding a thread. It declares exactly one compile
dependency of its own, `jspecify`, which `langchain4j-core` already brings — so the tree
gains a node and no subtree.

**It is not excluded, and the difference from `opennlp-tools` is the whole point.** The
opennlp exclusion is safe because the only class referencing it, `DocumentBySentenceSplitter`,
does RAG splitting, which [ADR-0003](../adr/0003-bundle-holds-config-shaped-inputs-only.md)
puts permanently out of scope. mutiny-zero backs the reactive `AiServices` path — and *using*
`AiServices` was never out of scope, which is the distinction
[P21](#p21--what-the-library-leaves-available-said-out-loud) spent a whole item making clear.
Excluding it would remove the jar from every consumer's classpath to save 58 KB and break the
new API for anyone who reaches for it. Nothing in ADR-0020's argument changes; the number it
rests on does, and M0 now carries the new one.

#### 2. The bump does not build without a `jspecify` pin

`mvn clean install` at `1.20.0` / `1.20.0-beta30` fails in `modelrack4j-provider-glm`, on the
`DependencyConvergence` rule:

```
org.jspecify:jspecify:1.0.1  <- langchain4j-core 1.20.0
org.jspecify:jspecify:1.0.1  <- langchain4j 1.20.0 -> mutiny-zero 1.3.1
org.jspecify:jspecify:1.0.0  <- langchain4j-community-zhipu-ai 1.20.0-beta30 -> guava 33.6.0-jre
```

`langchain4j-core` moved jspecify from `1.0.0` to `1.0.1`; guava stayed at `33.6.0-jre` in
both community betas and still brings `1.0.0`. Neither BOM manages jspecify, so nothing
reconciles the two. Fixed the way `slf4j-api` already is: a `<jspecify.version>` property and
a `dependencyManagement` entry in the parent POM, with a comment saying which two paths
disagree and to re-check it on the next bump.

This is the same class of failure M0 recorded for the GLM module's `jjwt` — the rule is on
precisely because nearest-wins would otherwise pick a version by depth and flip silently.

#### Everything the pin gates, re-checked against the 1.20.0 artifacts

Read from the jars, not from the release notes.

| Gated by the pin | Re-checked against | Result |
|---|---|---|
| [Task 0.2](phase-0-verification.md#task-02--verify-the-java-baseline) — Java baseline | bytecode of `langchain4j-core-1.20.0` and `langchain4j-1.20.0` | major **61** (= Java 17) on all **752** classes, no `META-INF/versions` overlays — `release` 17 stands |
| [Task 0.5](phase-0-verification.md#task-05--confirm-interface-names) — interface names | both jars' entry listings | all nine types unmoved — the set Task 0.5 tabulates in eight rows, counting the two window memories as one; `ChatMemoryProvider`, `MessageWindowChatMemory` and `TokenWindowChatMemory` still aggregate-only; `TokenCountEstimator` still in `dev.langchain4j.model`; `ChatLanguageModel` still absent. The five request/response types the SPI touches are unmoved too |
| [Task 0.6](phase-0-verification.md#task-06--provider-capability-matrix) — capability matrix | the four provider jars | unchanged: `OpenAiModerationModel` and `OpenAiTokenCountEstimator` in `-open-ai`; an estimator and no moderation model in `-anthropic` and `-google-ai-gemini`; neither in `-community-zhipu-ai` |
| [ADR-0020](../adr/0020-core-depends-on-langchain4j-aggregate.md) — the `opennlp` exclusion | `langchain4j-1.20.0.pom` | still a compile-scope dependency of the aggregate, now at `2.5.11`, so the exclusion is still required |
| [ADR-0018](../adr/0018-manage-langchain4j-versions-via-bom.md) — the two release lines | `langchain4j-bom-1.20.0.pom` | unchanged: `langchain4j.stable.version` = `1.20.0`, `langchain4j.beta.version` = `1.20.0-beta30`; `langchain4j-google-genai` has no plain `1.20.0` (HTTP 404) and is still beta-only |
| `examples.conf`'s enum claim | `javap` on the two enums | still true at `1.20.0`: `GPT_5_1` is in `OpenAiChatModelName`, and `AnthropicChatModelName` still has no `CLAUDE_SONNET_5` |

Dates are `Last-Modified` on the artifacts, per Task 0.1's caveat: the stable BOM 2026-09-04
11:49 GMT, the community BOM the same day at 13:17 GMT.

#### What the release notes flag that does not reach us

- **Non-blocking / reactive AI Services** — additive and `@Experimental`; the synchronous and
  `TokenStream` APIs are untouched. It is the reason mutiny-zero appeared, and it changes
  nothing this library builds. A consumer can use it, which is why the jar stays.
- **Jackson 3 opt-in**, through a `langchain4j-jackson3` artifact nobody has to add. Not
  added here: core takes four compile dependencies and this would be a fifth for no benefit
  to a configuration loader.
- MCP, agentic, A2A, embedding stores, RAG, watsonx, Bedrock — outside what a bundle holds.
- Gemini gained context-cache management, and thinking support at `1.19.0`. Both are
  provider-builder features the schema does not expose, so they neither help nor break here.

#### Verified

- `mvn clean install`: green on all eight reactor projects, **189 tests**, the same count as
  before the bump — a dependency bump changes no test, and the number is here so the next
  bump has something to compare against.
- The convergence failure above was reproduced first, then fixed, then the build re-run: it
  is a fix, not a precaution.
- `build/check-docs.py`: clean, 49 ADRs and 64 tracked markdown files.
- PIT on core after the bump: **198 mutants, 195 killed, 1 timed out, 1 survived,
  1 uncovered, 2 minutes 24 seconds** — the same five counts
  [P29](#p29--a-global-check-and-the-eight-things-it-found) recorded. The survivor and the
  uncovered line are the two it named: `LlmRegistry.reload` line 339, the equivalent
  `Optional.empty()`, and `WritableFileConfigSource.stage` line 139, the cleanup branch left
  untested on purpose. The timeout, which P29 counted without naming, is
  `LlmRegistry$Builder.chooseNotifier` line 760 in this run. A dependency bump should move
  none of this, and it moved none of it.
- `./run-atomic.sh` and `./run-database.sh` re-run against the new jars: both green, and
  neither contacts a provider. `./run-swap.sh` was also run and **did** reach OpenAI — one
  chat call on a key that was in the environment, which was not the intention but is the only
  live evidence in this entry: `gpt-5.1` answered through `OpenAiChatModel` at `1.20.0`.
- **Not run: `mvn -Pintegration verify`.** It needs real keys, and it is the only check that
  a configured model identifier still exists ([P6](#p6--the-integration-tests-against-live-apis)).
  A version bump does not rot a model name, but this entry claims nothing about the live APIs.

---

### P35 — Four corrections from a Java review

**Status:** Done — a renumbered ADR left four wrong citations in `src/main`, and the examples
had a swallowed reader failure and a closed `System.in` ·
**Raised by:** a review of the whole tree against the project's Java-17 guidance

A review read the whole of `src/main` — core, the four providers, the examples — with the five
questions and the Java 17 profile. It was run against D6's branch before that branch merged,
where `src/main` was 5,826 lines; `main` was 5,767 at the time and is 5,865 now that D6 has
landed, so that figure names the tree that was read rather than a count anybody can reproduce
today. It found nothing unsafe in the library's runtime path. It found four other things, and
the first is the one worth remembering.

#### The ADR renumbering did not reach the javadoc

Four javadoc comments cited **ADR-0053** for the decision that a layer answers for itself. That
is [ADR-0051](../adr/0051-layer-answers-for-itself-adapted-at-the-boundary.md). ADR-0053 is the
`ConfigAccessException` split — a real, accepted, *different* decision, so a reader who followed
the reference arrived at the wrong document rather than at no document, which is the worse
failure of the two.

| File | What it said |
|---|---|
| `Layer.java` | "every use site calls a method instead (ADR-0053)" |
| `ConfigLoader.java` | "resolves beside it (ADR-0042, ADR-0053)" |
| `LlmRegistry.java` | "rather than being recognised here (ADR-0053)" |
| `FileBacked.java` | "an application's own source cannot join them (ADR-0053)" |

`ADR-0051` was cited in **zero** Java files; all four sites were on `main`, merged in `23d882b`.

P31's own commit message is where the cause is written down: *"It takes 0051 rather than the
next free number because this branch merges first: D5 and D6 hold 0051 and 0052 on unpushed
branches and must renumber to 0052 and 0053."* The renumber ran over the ADR filename, the index
in `docs/adr/README.md` and the commit message. It did not run over the javadoc that had been
written against the pre-renumber number, and nothing else would ever re-read those four lines.

`AGENTS.md` and `docs/tasks/` had 0053 pointing at the right decision throughout, so the
documentation pass gave no signal. This is P16's lesson arriving a second time by a second
route: `modelrack4j-core/src/main` is not filed under "documentation", so a check scoped to
documents does not reach it. The counting rule in `AGENTS.md` now says a renumber is a
tree-wide `grep`, not a rename.

Verified afterwards: no `ADR-0053` remains anywhere under `modelrack4j-*/src`. `src/main` holds
28 ADR citations in all; the four above were wrong, and the other 24 were checked one by one
against `docs/adr/` and are correct.

#### `AtomicSnapshot` hid a reader failure and could report mid-flight

Two defects in the harness that *measures* the atomicity guarantee, which is what makes them
worth more than their size.

The `Future` from `pool.submit(reader)` was discarded. A reader that threw would have parked its
exception in a `Future` nobody read: that thread stops counting, and the run prints a smaller,
entirely plausible number with nothing to say a thread had died. An example whose whole output
is a measurement cannot swallow the failure of one of its four measuring threads.

And `report(readers)` could run while the readers were still writing. On the branch where
`awaitTermination` returns `false`, the code called `shutdownNow()` and fell straight through to
`report`, which iterates `Reader.observed` — a plain `LinkedHashMap` — and reads two plain
`long` counters that live threads may still be incrementing. Unlikely to fire, since
`stop.countDown()` precedes the `finally`; it is on the path that exists for when things go
wrong, which is the path that gets no other testing.

One change closes both. The futures are kept and drained through `awaitReaders`, which unwraps
`ExecutionException` so the reader's own failure is what surfaces, and cancels on a timeout.
`Future.get()` is also the *documented* happens-before edge for the counters `report` then
reads; `awaitTermination` alone only supplies one through `ThreadPoolExecutor`'s internal lock,
and supplies none at all on the branch where it times out.

The `finally` block keeps `shutdown()` / `awaitTermination` unchanged, including its comment
that `ExecutorService` is not `AutoCloseable` on Java 17 — that part was already right.

#### `ConsoleChat` closed `System.in`; `ThreeModelCouncil` documents that as wrong

`ConsoleChat` had its console reader inside the try-with-resources, so leaving the chat closed
`System.in`. `ThreeModelCouncil` keeps its reader outside and carries the comment saying why.
Neither was broken — `ConsoleChat` closes it as the last act of `main`, so nothing observes the
closed stream — but two shipped examples took opposite positions on one question, and these are
files people copy. `ConsoleChat` now matches, with the same one-line comment and a pointer to
its twin.

#### The `toString` redaction had no guard against drift

`LlmConfig.toString()` is hand-written to hide `apiKey` (ADR-0047), and it was complete: all
twelve components printed. Nothing kept it that way. A thirteenth component would compile, print
nowhere, and be missing from every log line, and `toStringRedactsTheApiKey` asserted only four
component names — `name`, `provider`, `modelName`, `apiKey=***` — so it would not have noticed.

The test now asks the record for its components instead of listing them:

```java
for (RecordComponent component : LlmConfig.class.getRecordComponents()) {
    assertThat(described)
            .as("toString() omits the component '%s'", component.getName())
            .contains(component.getName() + "=");
}
```

`redactionDoesNotWeakenEquality` is untouched: it already pins the other half, that `equals` was
*not* redacted, which is what keeps a rotated key a configuration change.

#### Verification

The guard was proved by making it fail, not by watching it pass. A throwaway probe deleted the
`streaming` component from `toString()`; the four original assertions still passed and the new
loop failed, naming it:

    [toString() omits the component 'streaming']
    Expecting actual:
      "LlmConfig[name=SL, ..., logResponses=false, memory=Optional.empty, moderationEnabled=false]"
    to contain:
      "streaming="

That also settles what the old test would have done on that day: nothing. `LlmConfig.java` was
restored from a copy taken before the probe and `git diff` confirms it is byte-identical.

Both changed examples were run rather than reasoned about (the P18 rule). `./run-atomic.sh`
completed with 178,157,354 samples across four readers, `updated=[SH, SL]` from the single save,
and both torn counters at zero — the readers all finished and reported, so `awaitReaders`
returned cleanly. `ConsoleChat` was driven with a piped `/exit` over a literal-key config and
printed its menu and `bye.` with no request sent.

`mvn clean install` is green: the full suite, all seven modules.

No public API changed and no behaviour changed, so there is no CHANGELOG entry — three of the
four are comments and a test, and the fourth is example code.

---

### P36 — Housekeeping: a test that raced its own listener, and the first read of five merged branches

**Status:** Done — two defects in `ReloadTest`, one of them a concurrency test that stayed
green when it detected the thing it exists to detect; the combined documentation read found
nothing to correct ·
**Raised by:** the owner, who asked for one branch to carry small work instead of one branch
each

This entry is a batch on purpose. Five items in a row (P26, P29, P32, P33, P35) were each a
branch, a task entry, a pull request and five checks for a fix measured in minutes, and the
verification was then repeated from the start for the next one. Small findings now collect
here until there are enough of them to be worth a branch.

#### `ReloadTest` waited on the registry and then read a counter the listener writes

`rapidWritesCollapseIntoOneReload` ended:

```java
awaitModel("SL", "v5");
assertThat(reloads).hasValue(1);
```

`awaitModel` polls `registry.get(name).config().modelName()`. `LlmRegistry.reload()` publishes
first and announces afterwards:

```java
bundles = staged;   // THE swap: one write, whole snapshot, nothing torn
notify(reloadListeners, change, "reload");
```

So the model a reader sees through `get()` can arrive *before* the callback that announces it,
and the assertion above reads a counter that the listener has not written yet. The window is
the width of one `notify` call, which is why no run has ever lost the race.

A probe made it visible rather than arguing about it. A first listener that sleeps 500 ms
widens the window; a second one counts. Inside the sleep:

    PROBE: get() reports model-name=v1 while the reload count is still 0

**Six of the twenty-one tests in the class stood on that ordering**, not one — the other five
read `changes.get(0)` the same way, which fails as an index error rather than as a wrong count.
Proved by widening the window in `LlmRegistry` itself, by 300 ms between the swap and the
notify, and running the suite unchanged: 4 failures and 2 errors, and the two errors were
`ArrayIndexOutOfBounds: Index 0 out of bounds for length 0` from `addedNameBecomesAvailable`
and `removedNameThrows`.

The fix is one helper, used at six call sites:

```java
private void awaitReloads(int expected) {
    await().pollDelay(Duration.ZERO).atMost(TIMEOUT).until(() -> reloads.get() >= expected);
    assertThat(reloads).hasValue(expected);
}
```

It waits on the callback and then pins the count, so it asserts exactly what the old line
asserted and nothing more. `pollDelay(Duration.ZERO)` keeps the cost at nothing: the condition
is already true by the time it is asked.

With the widened window still in place the fixed class is green, which is what says the helper
is not vacuous. `LlmRegistry` was then restored from `git` and the whole build re-run.

#### The debounce itself was not the fragile part

The suspicion worth ruling out was the other one: five writes into a 60 ms trailing-edge
debounce, where one stall longer than the debounce would split the burst into two reloads. A
probe ran the burst 60 times and measured the gap between consecutive writes. **Worst gap
3.43 ms against 60 ms**, and no run saw a reload count other than 1. Measured on the project's
usual machine — AMD Ryzen 7 7840HS, ext4 on NVMe, Pop!_OS 24.04 (kernel 7.0.11), Temurin
25.0.3 — which is one machine and not a benchmark; a loaded CI runner has less room. The
margin is about seventeen-fold, so the debounce constant is left alone.

#### The combined read of five merged branches

`#50` to `#54` merged on 2026-09-04 within four hours, and each was verified against its own
code only. Four documents had been touched by two or three of them and had never been read as
one text: `AGENTS.md`, `docs/tasks/README.md`, `docs/tasks/post-v1.md` and
`docs/manual/part-2-reference.md`.

Nothing needed correcting. Recorded because a check that finds nothing is only worth its cost
if the next session can see it ran:

- Both counted lists in `AGENTS.md` still match their bullets — "Four consequences" over four,
  and P20's "Five things" over five, that last one having gained its fifth bullet from D6.
- The `open-decisions.md` claim in `AGENTS.md`, D1–D6 all settled, matches the file: six
  entries, six `Settled` or `Closed` statuses.
- In `part-2-reference.md` the `build()` row carries both branches' clauses, and Troubleshooting
  has P24's rewritten `watch(true)` row followed by D6's three, in that order.
- `build/check-docs.py`: 53 ADRs, 68 tracked markdown files, no problems.

One thing looked like a defect and was not. Every message the Troubleshooting table quotes was
grepped for in `src/main`; `watch(true) watches configuration files, and none of these layers
is one` returned nothing. The string is split across two source lines between `is` and `one`,
so the table is right and the grep was wrong. A quoted message has to be matched against the
concatenated literal, not against the file.

#### A Java review of the test tree, which had never had one

P35 read the whole of `src/main` and stopped there. The defect above came from `src/test`, so
the batch went back over all 3,806 lines of core's tests against the project's Java-17
guidance. Five findings, all in the tests, none in the library.

**A concurrency test that could not fail for the reason it existed.** `getIsSafeDuringReload`
is what defends ADR-0038: four reader threads assert that a bundle and the config it reports
belong together, while ten reloads run underneath. It discarded all four `Future`s, so a
reader that *found* a torn bundle threw its `AssertionError` into a `Future` nobody read, and
died quietly while the other three carried on. The only net was `reads > 0` at the end, which
catches just the case where every reader dies at once.

Measured rather than argued. Failing one reader once, after 5,000 reads — the shape of a rare
torn read — left the suite green:

```java
if (reads.incrementAndGet() == 5_000) {
    throw new AssertionError("a torn bundle, deliberately");
}
```

    Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS

The `Future`s are now kept and re-raised, and the same sabotage fails the test naming its
cause: `ExecutionException: AssertionError: a torn bundle, deliberately`. **The project had
already written this rule down twice** — `ConfigSourceTest` keeps its futures with a comment
saying an exception "lands in its Future and nowhere else", and P35 fixed the same thing in
`AtomicSnapshot`. This was the site both passes missed, which is the P16 lesson again: a rule
recorded in one file does not check the file next to it.

**An assertion inside `finally` can replace the failure it was meant to report.** Four sites
asserted `pool.awaitTermination(...)` in a `finally`; when the body fails *and* the shutdown
times out, JUnit reports the shutdown and the real cause is gone. Three now fall back to
`shutdownNow()` — which the Java-17 profile asks for anyway, and which none of them did, so
the threads outlived the test — and the fourth moved to after the `try`, where it still
asserts a clean shutdown without competing.

**A fixed `Thread.sleep(600)` became an Awaitility window.** `store_leavesAWakingWatcherNothingToPublish`
slept, then looked once. It now requires the reload count to stay at zero for 300 ms, six
times the 50 ms debounce — a stronger claim, and faster: **0.417 s against 0.608 s**, taking
the class from 0.874 s to 0.666 s. It had been the slowest test in it by a factor of nine.

**A POSIX guard present in one test and missing in its neighbour.**
`store_keepsTheFilesPermissions` skipped itself where POSIX permissions do not exist;
`store_ontoAReadOnlyDirectory_failsAsAccessNotValidation` set them unguarded, which is an
`UnsupportedOperationException` there. Both now also skip under `root`, which ignores the
permissions the test depends on and would have failed it for a reason that says nothing about
the code.

**Registries built and never closed.** `LlmRegistryTest` left 12 open and
`LayeredResolutionTest` 5. Harmless today — `close()` is a no-op with no notifier, which was
checked rather than assumed — but the tests were silently relying on that. Both classes now
track what they build and close it in `@AfterEach`, the shape `ReloadTest` already used.

Two things looked like findings and were not, both checked before being written down: the two
`.formatted(...)` calls in the examples take only `%s` with `String` arguments, so `Locale.ROOT`
would change nothing, and `ConfigSourceTest:490` asserts a shutdown inside its `try`, which is
the correct place.

#### The documentation pass, which found the one defect the code review could not

Re-reading `AGENTS.md` end to end after editing it — the P19 rule, that a diff hides the
lines describing what you changed — turned up a command that had never worked. The Build and
test block gave this as its example of running a single method:

```bash
mvn -pl modelrack4j-core test -Dtest='LlmRegistryTest#reloadSwapsAtomically'
```

**No method of that name has ever existed in this repository.** It arrived with the first
guidance commit on 2026-07-26 and survived the move to `AGENTS.md` in P23. What makes it
worth an entry is the failure mode rather than the typo: scoped with `-pl`, a `-Dtest=`
method name that matches nothing does not fail.

    [INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0
    [INFO] BUILD SUCCESS

A session that followed the line would read the green and conclude the test passed. The
command now names `LlmRegistryTest#unknownNameThrows`, which was run and reports one test, and
the paragraph under the block says that a name matching nothing is a silent no-op.

`CONTRIBUTING.md` already said "a test that cannot fail is worse than no test" and told a
contributor to break the code and confirm the test catches it. That is right and covers the
single-threaded case; it does not cover the one above, where the assertion runs on a thread
whose failure never reaches the test. It gained one line for that, in the user-facing
register.

**`docs/tasks/milestones.md` was checked and deliberately left alone.** Its M1 table says
`LayeredResolutionTest` has 6 tests and `LlmRegistryTest` 18, against 7 and 25 today. That
table is the record of what M1 built, under a heading that says so and with a note explaining
one later growth — editing it to today's counts would misdescribe the milestone rather than
correct it. The same reading applies to ADR-0038's "four tests in `ReloadTest` pin the
behaviour", which is still true and, being an accepted ADR, frozen anyway.

The three other documents that name the changed tests were read and need nothing:
`milestones.md` describes `rapidWritesCollapseIntoOneReload` as "five writes, one reload" and
`getIsSafeDuringReload` as "four reader threads across ten reloads never see a bundle whose
config belongs to another block", both still exactly what the tests do. `./run-atomic.sh
--help` was run and prints what `AGENTS.md` claims for it.

#### Verification

`mvn clean install` green across all seven modules; core is 148 tests, unchanged — every
finding was an assertion that did not hold, not a case that was missing. `ReloadTest` is 21
tests in 6.59 s. No production code and no public API changed, so there is no CHANGELOG
entry.
