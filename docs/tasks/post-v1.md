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

#### Not done

The examples module still depends on OpenAI and Anthropic only, so nothing exercises Gemini or
GLM. Adding them is easy; making a *demo* out of them is not, because mandatory `${VAR}`
substitution is all-or-nothing per snapshot — a four-provider example file needs all four keys
or nothing loads. Not worth solving for a demonstration.
