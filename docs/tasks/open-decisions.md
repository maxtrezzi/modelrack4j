# Open decisions

Items waiting on the owner rather than on work. Do not resolve these unilaterally — each
one closes by writing an ADR (see [ADR-0001](../adr/0001-record-decisions-as-adrs.md)).

**D1 to D6 are settled; [D7](#d7--custom-properties-on-a-configuration-block) is open**, so
this file is a queue again. A new
entry here is a question for the owner, not work to pick up, and an entry marked
`Needs decision` blocks the code that depends on it rather than inviting a guess. Entries stay
in number order and keep the framing they were decided under, with the outcome at the top.

---

### D1 — GLM route if no maintained module exists

**Status:** Closed 2026-08-20 without a ruling — never became live ·
**Settled by:** [Task 0.3](phase-0-verification.md#task-03--glm-module-status)

Task 0.3 found `langchain4j-community-zhipu-ai` maintained and current at `1.19.0-beta29`;
the "removed in 1.3.0" report was a beta-suffix misreading, answered by the project lead on
the upstream issue itself. The native module is used and the fallback below is not taken —
[ADR-0022](../adr/0022-glm-via-the-community-module-and-its-bom.md), which also records the
second BOM import the module requires and the beta-line dependency it entails.

M4 is no longer blocked on this. The provider set is unchanged, so
[ADR-0005](../adr/0005-provider-factory-spi-via-serviceloader.md) needs no amendment.

**As posed, now the rejected alternative.** Had no maintained module existed, the fallback
was a `GlmProviderFactory` built on `langchain4j-open-ai` pointed at Zhipu's
OpenAI-compatible endpoint: it works, but GLM-specific parameters are unreachable through
the OpenAI-shaped API, and the library would be depending on an endpoint compatibility
guarantee it does not control. Kept here because ADR-0022 forecloses it deliberately — the
beta suffix on the native module is not a reason to revisit this.

---

### D2 — Repository visibility

**Status:** Settled 2026-08-25 — **public now, not released** ·
**Settled by:** [ADR-0034](../adr/0034-the-repository-is-public-before-it-is-released.md)

The owner made the repository public on 2026-08-25, taking the *public now* option below.
The decision is narrower than the switch it was made with: public and released are separate
acts, and only the first was taken. The version stays `0.1.0-SNAPSHOT`, there is no tag, no
GitHub release and no announcement, and M6 keeps its own trigger unchanged. ADR-0034 records
why, and what being readable now makes non-negotiable — `brainstorm/` as a confidentiality
boundary rather than a convention, secret discipline that is pre-push rather than
pre-release, and history that can no longer be quietly rewritten.

The framing the decision was taken under follows.

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

**Corrected 2026-08-20:** the "nothing has left the machine" premise this entry was written
under no longer holds. A remote *is* configured — `github.com/maxtrezzi/modelrack4j`, created
2026-07-26 — and the work to date has been merged through pull requests. It is **private**,
confirmed in [Task 0.7](phase-0-verification.md#task-07--name-and-coordinates).

That changes the framing, not the decision. The repository is on GitHub rather than only on
this machine, so "public from day one" is no longer available as an option — the live choice
is *public now* or *public at first release*. The one-way property still stands: private can
become public, but what has been public cannot be made unseen.

Task 0.7 also removed one thing that might have forced the answer: Maven Central publishing
does **not** require this repository to be public, because namespace verification uses a
separate temporary repository ([ADR-0025](../adr/0025-fix-coordinates-under-io-github-maxtrezzi.md)).
JitPack and the readability of the ADRs remain the two real arguments.

**On settling:** write an ADR, and if the answer is "public at first release", note what
triggers the switch so it does not drift.

---

### D3 — Token-window memory on a remote estimator

**Status:** Settled 2026-08-21 — **opt-in** ·
**Raised by:** [Task 0.6](phase-0-verification.md#task-06--provider-capability-matrix) ·
**Settled by:** [ADR-0027](../adr/0027-remote-token-counting-is-opt-in.md)

The owner chose the opt-in option below. `memory.type = token-window` on a `REMOTE`-estimator
provider fails validation unless `memory.allow-remote-token-counting = true` is set; the
default is `false`, and the failure message must name the flag. `ABSENT` (GLM) is not
escapable. On `LOCAL` providers the key is permitted and inert, so a config layer spanning
several providers need not be split. M2 unblocked.

[ADR-0021](../adr/0021-token-estimation-is-universal-but-two-cost-classes.md) established
that all four providers ship a `TokenCountEstimator`, but only OpenAI's counts locally.
Anthropic's and both Gemini ones make an HTTP call, with an API key and a timeout.

`TokenWindowChatMemory` calls the estimator on eviction. So `memory.type = token-window` on
those three providers puts a billed, rate-limited, failure-prone network call into ordinary
conversation turns — inside a component applications reasonably assume is local bookkeeping.

ADR-0021 fixes the *capability model* (three-valued, so validation can tell the cases apart)
but deliberately not the *policy*, because each option changes the config schema, which is
the plan's to change and not an ADR's:

- **Reject** — `validate()` fails the configuration. Safest, and wrong for anyone who
  genuinely wants accurate remote counts and has priced it in.
- **Warn** — build it, log loudly once. Nothing is forbidden, but a warning in a log is a
  weak signal for a per-turn cost, and this project's whole posture is fail-fast validation
  ([ADR-0008](../adr/0008-fail-fast-validation-staged-build-atomic-swap.md)).
- **Opt-in** — reject unless the config says so explicitly, e.g. a
  `memory.allow-remote-token-counting` flag. Fail-fast by default, escapable on purpose.
  Costs one schema key, and [ADR-0010](../adr/0010-discriminators-only-with-two-real-variants.md)
  is hostile to keys that do not earn their place.

**Blocks:** M2 (memory construction) and the `validate()` implementations in M4.

**On settling:** write an ADR; if the answer adds a config key, the schema in the plan
changes with it.

---

### D4 — Mutation testing in CI

**Status:** Settled 2026-08-31 — **never** ·
**Raised by:** [ADR-0041](../adr/0041-mutation-testing-on-core-only.md) ·
**Settled by:** [ADR-0043](../adr/0043-keep-mutation-testing-out-of-ci.md)

ADR-0041 configured PIT on core and refused to decide this one, for a stated reason: the run
took an unknown time, and
[ADR-0040](../adr/0040-protect-main-with-required-checks-not-required-review.md) makes every
required check a gate that every pull request waits on. The owner chose **never**,
in any form — not a required check, not an optional job, not a nightly.

The measurement that was missing is now taken: a full run on core is **122 s** on the
development machine, against **about 42 s** for the whole current gate, whose five checks run
in parallel and whose slowest leg was 42 s in run `33320243644`. So PIT would roughly triple
what a pull request waits for.

But duration turned out not to be the deciding argument. Two of the 153 mutants cannot be
gated on at all: one is *equivalent* and unkillable by construction, so a 100 % threshold is
permanently red, and one is a deliberate timeout whose outcome depends on how loaded the
machine is. `mutationThreshold` is `0`, so a job would be permanently green and certify
nothing. And the deliverable ADR-0041 fixed is a survivor list to read, which CI has nobody to
read.

**The rejected alternative was a nightly**, not a required check: no gate cost, no pass-or-fail
problem, the report published as an artifact. It loses because nobody is obliged to read it,
and because it still puts `mutationCoverage` in a file — and the only thing keeping PIT away
from the provider modules and their paid `*IT.java` suites is that a person types
`-pl modelrack4j-core`.

---

### D5 — A version token for optimistic concurrency

**Status:** Settled 2026-09-03 — **no token; the signature stays** ·
**Raised by:** putting `storeIfUnchanged` behind an HTTP `PUT` ·
**Settled by:** [ADR-0052](../adr/0052-no-version-token-the-expected-text-is-the-token.md)

The owner took the first option below. `ConfigSource` keeps `id()` and `text()`,
`storeIfUnchanged` keeps its signature, and there is no `version()`, no `storeIfVersion` and no
digest-taking overload. An application that wants an `ETag` derives one from `text()` and keeps
the digest to itself — and because that is the argument the decision rests on, the recipe is now
in the reference rather than only in the ADR
([P32](post-v1.md#p32--the-recipe-d5s-argument-rests-on)).

**The framing below contains a false premise, and it is worth recording rather than editing
away.** The entry says the caller "has to ship the entire previous document, encoded, in a
header". It does not. The server that answers the `PUT` is the one that read the layer, so it
can digest `text()` itself, compare that with `If-Match`, answer `412` on a mismatch, and pass
the same text it just read as `expected`. The client ships a token; no document crosses the
wire. Reading the code is what showed this: `storeIfUnchanged` re-reads under the reload lock,
so the window between the server's read and its write is closed by the library rather than by
the header.

That correction is most of the answer. What remained was a real trade — a token is natural over
a network and cheap for a client to hold — and it lost to permanence: `0.1.0` is published, the
pattern is four lines of application code today, and a published method cannot be withdrawn.

The framing the decision was taken under follows.

`storeIfUnchanged(target, expected, text)` takes the whole expected document. Over HTTP that is
an ETag-shaped problem solved without an ETag: the caller has to ship the entire previous
document, encoded, in a header, because there is nothing smaller that means the same thing.

The options, all cheap, none obviously right:

- **Leave it.** The current signature cannot be misread, and a caller that already holds the
  text it edited pays nothing. Anything smaller is a hash, and a hash is a second way to say
  the same thing that can disagree with the first.
- **`String version()` on `ConfigSource`** — a digest of `text()`, with a
  `storeIfVersion(target, version, text)` beside the existing method. Natural over a network,
  and it makes a layer's identity something a client can hold cheaply.
- **A digest-taking overload only**, leaving the interface alone.

Whichever wins, the byte-exact semantics stay: the point of the check is that a reformat or an
added comment is a change somebody made on purpose.

---

### D6 — "Cannot store" is not "your configuration is invalid"

**Status:** Settled 2026-09-03 — **a distinct exception, covering reads too** ·
**Raised by:** mapping the library's exceptions onto HTTP status codes ·
**Settled by:** [ADR-0053](../adr/0053-a-separate-exception-for-a-layer-that-cannot-be-reached.md)

The owner took the second option below, and widened it. `ConfigAccessException` is public,
unchecked, and **not** a subclass of `ConfigValidationException`, so catching one never catches
the other.

**Two corrections to the framing below, both found by reading the code rather than the entry.**

1. **The entry scopes the new type too narrowly**, to "only once validation has passed and the
   write itself fails". `WritableFileConfigSource.stage()` fails on an unwritable directory or a
   full disk **before** validation runs at all, and that is the commonest storage failure there
   is. Both `stage()` and `commitStaged()` moved.
2. **The read side carries the identical mislabel.** A layer that does not exist was also a
   `ConfigValidationException`. The owner chose to fix both sides, which is why the type is
   named for *access* rather than for storing.

**A third correction came from running the tests, and neither the entry nor the plan had it.**
Moving `FileConfigSource.read` was not enough to change what a missing file does at `build()`.
A file layer is parsed with `parseFile` and never through `text()` (ADR-0042), so a missing
file surfaces from `ConfigLoader`, where it had been wrapped as *"could not be parsed"* — which
is untrue twice over, since nothing was parsed and nothing was wrong with the text. The loader
now catches `ConfigException.IO` before `ConfigException`. Probed against `config-1.4.9`: a
missing file and an unreadable file are both `ConfigException$IO`, a syntax error is
`ConfigException$Parse`.

The framing the decision was taken under follows.

`store` and `storeIfUnchanged` throw `ConfigValidationException` both when the text does not
load **and** when the layer cannot be written — the javadoc says so plainly: *"if the text does
not parse or does not validate, or if it cannot be stored"*. An application that turns the
library's exceptions into HTTP responses has to answer `400` or `500` from that one type, and
gets it wrong for one of the two cases: a read-only file or a full disk reaches the client as
"your configuration is invalid", which it is not.

- **Leave it.** One exception type is one thing to catch, and 0.x churn has a cost that a
  permanent artifact makes permanent.
- **A distinct exception for the storage half** — `ConfigStoreException`, thrown only once
  validation has passed and the write itself fails. Breaking for anyone catching the current
  type narrowly, which the CHANGELOG reserves the right to be in a minor.

The rollback behaviour is not in question either way: a failed write already restores the
previous snapshot before the exception leaves the method.

---

### D7 — Custom properties on a configuration block

**Status:** Shape agreed 2026-09-06, no ADR written — the library carries the block as text and
never interprets it; what remains open is listed at the end ·
**Raised by:** the owner on 2026-09-06, wanting two or three application values to travel with
the connection they belong to

An optional sub-block on each named configuration, holding values the library **never
interprets**. It is carried as text, and an optional caller-supplied handler turns that text
into whatever object the application wants.

The entry records two discussions on the same day. The first produced six questions and a
recommendation beside each. The second answered three of them, then replaced the design the
other three were about, so those three no longer have anything to decide. The rejected shapes
are kept at the end, because the reasoning against them is what makes the current shape
defensible.

#### The shape

Nothing changes before the block is reached: layers are parsed, merged and resolved exactly as
they are today. Then, for each named configuration:

1. The `custom-properties` sub-block of the merged, resolved block is **rendered back to text**.
   `ConfigObject.render(ConfigRenderOptions.concise())` produces valid JSON — measured:
   `{"escalate-after":"45s","max-retries":3,"prompt-id":"support-v3","tags":["a","b"]}`.
   Absent means `{}`.
2. That text is a component of `LlmConfig`, so **the reload diff is a string comparison**.
3. If the caller registered a handler, it is called with that text in
   `SnapshotLoader.buildBundle`, beside `factory.validate` and `createChatModel`, and its result
   is a member of `LlmBundle` — a built object, like the chat model.
4. The caller reads `customPropertiesText()` always, and the parsed object when a handler
   exists.

**Why this shape rather than an accessor the library provides.** The owner's objection, which
is the reason the earlier design was dropped: a library that supplies typed accessors for values
it does not own has two reading mechanisms inside one object — manual reading for its own
schema, and a second mechanism for the application's. Carrying text has one rule instead: **the
library reads manually what it owns, and of what it does not own it knows nothing and passes the
text.**

It is also less library, not more, which is the right direction for ADR-0002. The core interprets
nothing here.

#### What the shape dissolves

Three of the six original questions no longer have anything to decide:

| # | Was | Now |
|---|---|---|
| 1 | The value type: `Map<String,String>` · `Map<String,Object>` · `CustomProperties` · `Config` | **No value type.** The library holds a string |
| 2 | How the keys are enumerated, and whether a nested object is refused | **The library does not enumerate.** Structure is the handler's business |
| 4 | A caller-supplied validator, and its signature | **The handler is the validator.** If the text does not become the application's type, it throws |

Question 3 — what `toString()` prints — is simplified rather than dissolved: the library holds
text that can contain a resolved credential, so `LlmConfig.toString()` must not print it. The
`isSafeToLog` interface discussed earlier is no longer needed, because the application's own
object has the application's own `toString()`, which is not the library's business.

Question 5 stands: the carrier and the handler ship together, since the handler is what makes
the carrier useful.

#### Question 6 is amended by the shape

It was answered **yes** — a `ProviderFactory` may read the custom properties — on the ground that
every `ProviderFactory` method takes `LlmConfig`, so the field is reachable anyway. Under this
shape what a provider reaches is **the text**, like everyone else. A provider could parse it, but
it cannot know the application's type, and a provider that parsed it would be doing exactly the
string lookups ADR-0002 names as what this library is not.

So the answer stands in letter and changes in effect: **there is no provider-facing shape.** The
owner confirmed this is intended.

Three things the earlier "yes" brought with it survive the amendment:

1. **The definition is "the core never interprets them"**, not "the library never reads them".
2. **Name divergence between providers** is no longer a risk worth managing, because there is no
   typed field for providers to disagree about.
3. **A custom property may still hold a credential**, which is why `toString()` matters.

#### The semantics, including every empty case

The handler is optional. The text is always present.

| block in the file | handler | `customPropertiesText()` | the object |
|---|---|---|---|
| absent | registered | `"{}"` | the handler is called with `"{}"` → a real object |
| present | registered | rendered JSON | a real object |
| absent | none | `"{}"` | — |
| present | none | rendered JSON | — |

Never `null`, never an empty string, and no case a caller has to distinguish before parsing.
Absent and present-but-empty render to the same text, so neither produces a false change in the
diff.

**With a handler registered the object always exists, including when the block is absent**,
because the handler is called with `{}`. That is not a formality: a rule of the form "an openai
block needs `prompt-id`" is violated exactly when the sub-block is missing, so a handler skipped
there could not enforce the rule that motivated the feature. Measured that `{}` yields an
instance rather than a failure:

```
readValue("{}") -> SupportProps[promptId=null, retries=0, tags=null]
```

**Without a handler the empty object is still one line away**, in the caller's own type:
`mapper.readValue(bundle.customPropertiesText(), SupportProps.class)`.

The one case with no object is asking for a parsed object without having registered a handler.
That is not a state of the configuration but a mistake by the caller — the library has no type
to build. Recommended: throw, with the precedent that `get()` on a name that was never
configured throws `UnknownConfigurationException` (ADR-0014).

#### Why the text goes in `LlmConfig` and the object in `LlmBundle`

This is the load-bearing part of the shape, and getting it wrong fails silently.

ADR-0006 makes the per-name reload diff record equality on `LlmConfig`. If the parsed
application object were a component, the diff would depend on **whether the application
implemented `equals`**. A class without it compares by identity, so every block looks changed on
every reload: every bundle rebuilt, every model reconstructed, `ReloadChange.updated()` naming
everything. Nothing warns.

Carrying the text instead makes equality exact and requires no contract from the application.
The parsed object then belongs where built objects belong — `LlmBundle` — and the carry-over at
`SnapshotLoader:118-121` keeps working: unchanged text, unchanged bundle, handler not re-run.

The reading path the owner chose is `registry.get("SL").customPropertiesText()`, so `LlmBundle`
carries a convenience method delegating to `config().customPropertiesText()`.

#### What this looks like in use

**None of this is implemented.** The names are placeholders for the shape under discussion.

```hocon
llm {
  SUPPORT {
    provider   = openai
    api-key    = ${OPENAI_API_KEY}
    model-name = "gpt-4o-mini"
    timeout    = 30s

    custom-properties {
      prompt-id      = "support-v3"
      max-retries    = 3
      escalate-after = 45s
      webhook-token  = ${?SUPPORT_WEBHOOK_TOKEN}
    }
  }
}
```

```java
LlmRegistry registry = LlmRegistry.builder()
        .configFiles(List.of(base, local))
        .customPropertiesHandler(text -> mapper.readValue(text, SupportProps.class))
        .build();

SupportProps props = registry.get("SUPPORT").customProperties(SupportProps.class);
String raw         = registry.get("SUPPORT").customPropertiesText();
```

The application's type carries its own rules, and the deserializer enforces them:

```java
record SupportProps(@JsonProperty("prompt-id")      String promptId,
                    @JsonProperty("max-retries")    int maxRetries,
                    @JsonProperty("escalate-after") String escalateAfter) {}
```

A handler that needs more than binding writes it, and throwing is how it rejects:

```java
.customPropertiesHandler(text -> {
    SupportProps p = mapper.readValue(text, SupportProps.class);
    if (p.maxRetries() < 0) {
        throw new IllegalArgumentException("max-retries must not be negative");
    }
    return p;
})
```

A rejection reaches the three paths that run `SnapshotLoader.load` exactly as any other
validation failure does: thrown to the caller on `build()`; on `reload()` nothing swaps, the
previous snapshot stays live and one `onReloadFailure` fires (ADR-0012, ADR-0031); on `store()`
nothing is written and nothing is announced, which holds for a layer that is not a file because
`LlmRegistry.storeHoldingTheLock:549-552` validates the staged layer list before anything
distinguishes a file from a text layer.

#### The three costs the shape accepts

**Per-value origins are lost inside the sub-block.** Rendering to text drops the file and line
attached to each value, so a handler's error cannot say `base.conf: 6`. Two things limit the
damage, and the second was measured rather than assumed:

- It is **only** the custom properties. Every key the library owns keeps its origin, because
  those are read straight from the merged `Config`.
- The merged sub-block's own origin **names every layer that contributed to it**:
  `merge of override.conf: 1,base.conf: 1`. So the library can say which block failed and which
  layers built it. Only "which key came from which file" is gone, and even that could be
  recovered by walking the sub-block once before rendering.

**A render and a re-parse per changed block per reload.** Trivial on blocks this size, and the
carry-over means it happens only for blocks that changed. The owner judged it negligible.

**The structural bound disappears** — nothing refuses a nested object or a list any more. This
is the smallest cost of the three, because the bound moves somewhere sharper rather than
vanishing. Measured: Jackson deserializes records natively and has
`FAIL_ON_UNKNOWN_PROPERTIES` **enabled by default**, so a typo is refused by the application's
own binder, naming what it did know:

```
Unrecognized field "retrys" (class SupportProps), not marked as ignorable
(2 known properties: "retries", "promptId")
```

The library could only have refused *structure*. The application's binder refuses *wrong names*,
which is what actually goes wrong.

#### Still open

- **Does the library wrap a handler's exception?** An application will throw
  `IllegalArgumentException`. Unwrapped, a caller catching `ConfigValidationException` around
  `build()` misses it and the message does not name the block. Recommended: wrap always, naming
  the block and keeping the original as the cause, so the block name never depends on how
  careful the caller was.
- **The unknown-key question below**, and whether it takes its own number.
- **Whether any of this lands in 0.2**, which the owner is considering.

No ADR exists yet. D7 closes by writing one (ADR-0001).

#### Spun out of this discussion, and not given a number

**Unknown keys that belong to the library's own schema are a separate question**, recorded here
rather than as its own entry because taking a number is the owner's call. It is about
`temperatur`, not about custom properties.

Measured on 2026-09-06 by running it — a throwaway test in core's test scope, since removed —
against a block named `SL` carrying `temperatur = 0.9`, `timeuot = 30s`,
`memory.max-mesages = 99` and an invented sub-block containing a list:

```
>>> LOADED. temperature = Optional.empty | timeout = PT1M
```

The registry builds and `get("SL")` works. Both misspelled values are gone: `temperature` falls
back to the provider's own default and `timeout` to the 60s in `modelrack4j-reference.conf`.
There is no exception and no warning, because `LlmConfig.fromBlock` reads only the paths it
knows and nothing enumerates the rest, so no code is in a position to notice.

The question is whether that becomes an error, a warning, or a builder-level choice. What the
discussion established:

- **A warning is worse here than elsewhere.** The configuration loads, and then the same warning
  repeats on every reload for as long as the file is not fixed. With an error the state cannot
  exist: an unknown key never reaches a live snapshot.
- **An error fits the reload model.** A rejected reload swaps nothing and keeps the previous
  snapshot live, so a typo cannot silently degrade a running application.
- **The order of reversibility favours strict now.** Strict to lenient is not a breaking change;
  lenient to strict is. `0.x` with one consumer is the cheapest moment there will be.
- **"A series of" is right even if the severity changes.** One error listing every offending key
  beats failing on the first, and each key can carry its origin: `origin().description()` was
  measured returning `base.conf: 6` for a file layer and `db-row:tenant-42: 3` for a text layer
  parsed with the `setOriginDescription` that `ConfigLoader.parse:118` already passes.
- **The custom-properties shape strengthens the case.** Jackson refuses an unknown field by
  default, which is the same rule applied by the party that knows the schema. For the library's
  own keys, that party is the library.
- **The honest cost is forward compatibility.** A file written for a later version that adds
  `top-p`, read by an earlier one, would fail rather than ignore the key.
- **The implementation risk is a second list that drifts.** `modelrack4j-reference.conf` holds
  only the defaults and deliberately omits both the required keys and the ones whose absence is
  meaningful, so it is not a schema and cannot become one without losing its purpose. A declared
  `Set<String>` beside `fromBlock` is a second copy of the same truth and no test would catch it
  drifting. The alternative is to let the parse produce the known set — a wrapper that records
  each path `fromBlock` asks for, so unknown keys are the leaf paths it never touched, and adding
  a key to `fromBlock` makes it known automatically.

**The scheduling consequence.** Putting a key the library does not know inside a block is legal
today and does nothing. Making it an error without shipping the custom-properties block in the
same version removes a capability that exists without providing the declared one that replaces
it. So strictness in 0.2 requires the block in 0.2; the reverse does not hold.

#### Smaller choices that follow, listed so they are not rediscovered

The key is `custom-properties` in the file, kebab-case like the rest of the schema. Absent
renders as `{}` rather than an `Optional`. The text is an ordinary record component, so an edit
rebuilds that bundle and reports the name in `ReloadChange.updated()` — ADR-0032 settled that for
`description` and explicitly forbids carving a field out of equality. `ConfigSource` and the
reload machinery are untouched. The handler is pure and fast, with no I/O and no blocking,
because it runs under `reloadLock`; that is the contract provider factories already live under,
and `SnapshotLoader.buildBundle` already runs `factory.validate` and `createChatModel` there, so
the hazard class is not new. One handler serves every block; a rule that applies to one branches
on the name.

ADR-0032's closing warning applies and should be quoted in the schema documentation: a field
that must not affect the diff does not belong in `LlmConfig` at all, so this is for small stable
values and is neither a cache nor a data channel.

**Why this is not the "generic reloadable configuration" ADR-0002 refuses.** The value is not
storage: an application can already keep its own HOCON file, and unknown keys inside a block are
ignored in silence today. What it buys is **atomicity** — a property inside the block is
validated, published and rejected in the same swap as the model it belongs to, so a reload
cannot leave a new prompt template beside an old model — and it buys that while the core
interprets nothing.

#### Shapes considered and rejected

**A typed value on the record** — `Map<String,String>`, `Map<String,Object>`, or a
`CustomProperties` wrapper over Typesafe Config's accessors. All three make the library provide
a reading mechanism for values it does not own, which is the second logic the owner objected to.
The measurements taken while comparing them are kept because they would decide the question
again if it reopened:

| in the file | `Map<String,Object>` | `Map<String,String>` |
|---|---|---|
| `retries = 3` | `Integer` | `"3"` |
| `big = 3000000000` | **`Long`** | `"3000000000"` |
| `ttl = 10s` | **`String`** | `"10s"` |
| `size = 10MB` | **`String`** | `"10MB"` |
| `list = [1,2]` | `ArrayList` | refused: *list has type LIST rather than STRING* |

`Map<String,String>` does not force quoting — `getString` converts every scalar — so the two
produce identical configuration files and differ only in the application's code.
`Map<String,Object>` preserves four scalar types and the line between `Integer` and `Long` is
invisible in the file, so `(Integer)` works until a value crosses it. Durations and sizes are
`String` under both, because they are interpretations applied by `getDuration` and `getBytes`
rather than types in the value tree. And a key cleared with `= null` in a higher layer arrives in
`unwrapped()` as a present key with a `null` value, so `Object` needs the same explicit null
handling a map of strings would.

The `CustomProperties` wrapper was the strongest of the three, because Typesafe Config's own
accessors are tolerant about form and strict about content and name the line — `getBoolean` on a
quoted `"true"` gives `true`, `getInt` on `"tre"` is refused, `getDuration("10s")` gives
`PT10S` — which no map reaches. It lost to the text carrier on the one-mechanism argument.

**Exposing Typesafe `Config` as the field type.** `LlmConfig.fromBlock(String, Config)` is
already an accidental leak that P29 and P38 both declined to make permanent; a second one would
be deliberate, and it would turn a Typesafe Config upgrade into a consumer-visible change.

**`ConfigBeanFactory` inside the library.** It exists in `config-1.4.9`, needs no new dependency,
and does more than expected — measured mapping `prompt-id` to `promptId`, `45s` to a `Duration`
and a list to `List<String>`. It refuses records: *"needs a public no-args constructor to be used
as a bean"*. Since ADR-0006 requires `LlmConfig` to be an immutable record whose equality is the
reload diff, it cannot bind the library's own schema, and under the text carrier the caller can
still use it inside a handler if they want JavaBeans.

**A `toJson()` accessor on a typed wrapper**, so the application could deserialize with its own
Jackson. Superseded: if the useful thing is the text, the text is what the library should carry.

**Carving the field out of `equals`** to avoid rebuilding a bundle when only a property changes:
forbidden by ADR-0032, which gives the reason — a carve-out produces a permanently stale
accessor. **Letting the handler transform the library's own configuration**: a live `LlmConfig`
that differs from the file makes the record-equality diff meaningless. **A per-snapshot handler**
seeing every block at once: out of step with the framing, since these properties belong to one
connection. **A handler named in the configuration file** rather than registered on the builder:
that means naming a class in a file, which is code-shaped and falls under ADR-0003.
