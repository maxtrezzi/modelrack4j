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
