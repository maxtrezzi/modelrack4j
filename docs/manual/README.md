# The modelrack4j manual

Two parts, with different jobs. Read the first one at a keyboard; come back to the second
when you need an answer.

| | For | Read it |
|---|---|---|
| **[Part 1 — Tutorial](part-1-tutorial.md)** | Someone who has not used the library | Start to finish, running the commands. About forty minutes. Six of its ten steps run offline; four send real requests — a dozen short prompts in total. |
| **[Part 2 — Reference](part-2-reference.md)** | Someone using it | By section. Every configuration key, every method, exactly what a reload guarantees, and a troubleshooting table. |

The tutorial builds on the runnable examples in
[`modelrack4j-examples`](../../modelrack4j-examples). Nothing in it is pseudo-code — every
command was run and every output block is a real capture.

Two tables cover them, with different jobs, and this page keeps neither. The root README's
[Runnable examples](../../README.md#runnable-examples) says what each one shows and what it
costs to run, and links to each source file. [Part 2's
Examples](part-2-reference.md#examples) says what claim each one pins down and which
credentials it needs.

The one worth knowing here: **two of the five are free.** `AtomicSnapshot` and
`DatabaseSource` send no request at all, so they need no credential and cost nothing — start
with either if you only want to see something work.

## What lives where

This manual is for **users** of the library. Two other sets of documents exist and are not
duplicates of it:

| | Answers |
|---|---|
| [`docs/adr/`](../adr/README.md) | *Why* is it built this way? What was rejected? |
| [`docs/tasks/`](../tasks/README.md) | What is done, what is next, what is waiting on a decision? |
| [`README.md`](../../README.md) | What is this, in five minutes? |

Where the manual and an ADR disagree about a *decision*, the ADR is authoritative and the
manual has drifted — please report it.
