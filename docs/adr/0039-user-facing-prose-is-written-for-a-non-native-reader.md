# ADR-0039: Write user-facing prose for a non-native reader, terse but self-explaining

- **Status:** Accepted
- **Date:** 2026-08-27
- **Supersedes:** —
- **Amends:** —

## Context

The repository has been public since [ADR-0034](0034-the-repository-is-public-before-it-is-released.md),
so its prose is now read by people who did not write it and cannot ask the author what a
sentence meant. That prose had drifted into a single house register — dense, aphoristic,
fond of compression — inherited from the ADRs, where the register is a deliberate choice
recorded in [ADR-0001](0001-record-decisions-as-adrs.md) and
[ADR-0015](0015-track-work-items-in-docs-tasks.md), and appropriate there: an ADR is read by
someone who already holds the context and wants the decision, not the explanation.

It stopped being appropriate when it reached the README, the manual and the public Javadoc,
which are read by someone acquiring the context for the first time.

The trigger was the owner — a technical reader, and the person the library is built for —
failing to parse one of the repository's own sentences. The README described the
`AtomicSnapshot` example as *"One save changes two models; four threads sample the pair both
ways and count the mixed ones — via `snapshot()` the count is zero by construction."* Every
clause is true, and at 26 words against 32 it is only six words shorter than what replaced it.
It is also undecodable unless the reader already holds the model of torn reads and generation
freezing that the sentence exists to teach.

A full pass over the user-facing text, recorded as
[P11](../tasks/post-v1.md#p11--the-user-facing-text-read-as-a-non-native-reader-would-read-it),
found this was not one bad sentence: ten metaphors of the same kind, nine vocabulary items
above B2, and six sentences whose grammar rather than vocabulary was the obstacle. It also
found two claims that were plainly wrong — a list of three introduced as two, and a Javadoc
line promising hot reload "when it arrives" three milestones after it arrived. Prose nobody
can read is also prose nobody proofreads.

Two constraints were fixed going in. The owner is not a native English speaker, and neither
are most of the developers who would reach for a LangChain4j configuration library. And
`brainstorm/` is private, so nothing in the tracked text can lean on context that lives only
in the plan.

## Forces

**Brevity is genuinely valuable, and the register was not simply wrong.** The compressed
style is why the README says useful things in five minutes and why the manual's reload
section fits on one screen. A rule that traded terseness for length wholesale would make
every document worse, and the tempting overcorrection — "explain everything" — produces the
padded, hedging documentation this project has avoided from the start.

**But brevity and obscurity had been conflated.** They are separable, and the distinction is
the whole decision: a short sentence that explains itself on a first reading costs the reader
nothing, while a short sentence that stands in for its meaning with a figure of speech costs
the reader a decoding step they can only perform if they already know the answer. "The count
is zero by construction" is not shorter than the mechanism it hides; it is the same length
and unreadable.

**Non-native readability is an independent axis from conceptual readability.** A sentence can
fail because the reader lacks the domain model, or because the reader lacks the idiom —
"page someone", "no ceremony", "release train", "a shorter fuse", "three days in" — and the
two failures need separate tests. Writing only for conceptual clarity leaves the idiom
problem untouched, which is what had happened.

**Three alternatives were considered and lost:**

- *Leave it and let readers ask.* The audience is anonymous and the medium is a README; a
  reader who cannot parse the pitch does not open an issue, they close the tab. This also
  ignores that the pass found real defects hiding behind the density.
- *Apply the rule everywhere, ADRs included.* Rejected because it mistakes one audience for
  all of them. An ADR's reader has the context by definition and is served by compression;
  flattening ADR-0012 into tutorial prose would lose the precision the decision record exists
  to hold, and would fight [ADR-0001](0001-record-decisions-as-adrs.md) for no reader's
  benefit.
- *Write a style guide listing banned words.* Rejected as the wrong instrument. The list
  found in one pass — "straddle", "hearsay", "inert", "bless" — is a symptom, not the rule; a
  banned-word list is both over-broad (each word is fine in some sentence) and under-broad (it
  cannot anticipate the next metaphor). A test applied per sentence generalises where a list
  does not.

## Decision

**User-facing prose is written for a technical reader at roughly B2 English who does not read
English as a first language, and every sentence must explain itself on a first reading.**

Two tests, applied per sentence, both of which must pass:

1. **Would a reader who does not yet know the mechanism parse this?** If the sentence only
   resolves for someone who already has the answer, unpack it — even at the cost of words.
   Metaphor is permitted only where it does work the literal statement cannot, and never as
   compression.
2. **Would a competent non-native reader parse this without a dictionary?** Rare vocabulary,
   idiom, phrasal verbs with non-literal meanings, and native-speaker ellipsis get replaced by
   the plain technical term. Domain vocabulary is not the target and is expected.

Being brief remains the default. The rule constrains *how* meaning is compressed, not whether.

**Scope: everything a consumer reads.** `README.md`, `docs/manual/`, public Javadoc, the
commented `.conf` files that ship as examples, `CONTRIBUTING.md` and `CHANGELOG.md`.

**Out of scope: `docs/adr/`, `docs/tasks/` and `CLAUDE.md`**, which keep their existing
register. Their audience already holds the context, and in the ADRs' case the body of an
accepted decision cannot be edited at all
([ADR-0001](0001-record-decisions-as-adrs.md)) — a style rule that reached them would be
unenforceable by construction.

## Consequences

**What is gained.** The user-facing text is now readable by the audience that actually has to
read it, and the pass that established the rule also removed two false statements that had
survived precisely because the surrounding prose discouraged close reading.

**What is accepted.** Some documents get longer. The figure is given as a measurement rather
than an impression because three drafts of this paragraph estimated it instead, and all three
were wrong: the commit that carried the pass took the README, both parts of the manual and
the CHANGELOG from 11,597 words to 11,752, or **155 words — 1.34%**. That commit also carried
an unrelated configuration fix, so a little of the 155 is not this rule's doing. The sentence
that triggered the rule went from 26 words to 32 where the README describes `AtomicSnapshot`,
and from 73 to 99 where the reference describes the same example. The answer to "can this be
shorter?" is yes, and it was, and it did not work.

**The rule is a habit, not a gate, and it will be violated.** It already was, inside the
commit that established it: the sentence added to explain the `claude-sonnet-4-6` model
choice was first written as *"a temperature welded into a builder call"* — reintroducing the
exact metaphor removed from the same file fifty lines above, three edits earlier. Recorded in
P11 rather than quietly fixed, because it is the honest forecast: a single pass does not
inoculate the next paragraph, and the next contributor writing in this repository's voice
will reach for the same compression. Expect to apply the two tests when writing, not only
when reviewing.

**No automated check enforces this, and none is planned.** `build/check-docs.py` verifies
links and ADR metadata — structural facts a script can decide. Whether a sentence explains
itself is a judgement, and a linter that approximated it (word lists, readability scores)
would produce false confidence in exactly the sentences that need a human. The enforcement is
review, and the trigger is a reader saying they did not understand something, which is how
this ADR came to exist.

**What a future contributor might be tempted to do, and what it would break.** Two things.
Applying this rule to the ADRs or to `docs/tasks/` would flatten records whose density serves
their readers and, for accepted ADRs, would violate the immutability rule outright. And
"simplifying" a user-facing paragraph back toward the old register — restoring a crisp
metaphor over a plain explanation, because the plain version reads as flat — reverses the
decision one sentence at a time, which is exactly how the prose arrived in the state P11
found it.
