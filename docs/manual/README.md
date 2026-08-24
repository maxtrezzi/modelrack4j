# The modelrack4j manual

Two parts, with different jobs. Read the first one at a keyboard; come back to the second
when you need an answer.

| | For | Read it |
|---|---|---|
| **[Part 1 — Tutorial](part-1-tutorial.md)** | Someone who has not used the library | Start to finish, running the commands. About forty minutes, and it spends a little money on real API calls. |
| **[Part 2 — Reference](part-2-reference.md)** | Someone using it | By section. Every configuration key, every method, exactly what a reload guarantees, and a troubleshooting table. |

The tutorial builds on the runnable examples in
[`modelrack4j-examples`](../../modelrack4j-examples). Nothing in it is pseudo-code — every
command was run and every output block is a real capture.

| Example | Shows | Cost |
|---|---|---|
| `AtomicSnapshot` | One save changes two models; threads sampling both never see a mixed pair | **free, no API key** |
| `ProviderSwap` | The same call site answered by one provider, then another, after a file edit | two requests |
| `ConsoleChat` | A menu of every configured model; edit the file while it runs | a conversation |
| `ThreeModelCouncil` | Three models, one question, no provider branch in the code | three requests |

`AtomicSnapshot` is the one to run first if you only want to see something work: it reads
configuration and sends no request, so it needs no credential and costs nothing.

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
