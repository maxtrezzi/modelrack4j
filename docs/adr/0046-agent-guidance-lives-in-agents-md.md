# ADR-0046: Agent guidance lives in `AGENTS.md`, with `CLAUDE.md` left as a pointer

- **Status:** Accepted
- **Date:** 2026-09-02
- **Supersedes:** —
- **Amends:** ADR-0037 — only the file's name and the pointer left behind. Everything ADR-0037
  decided about the guidance being *tracked and maintained* is unchanged.

## Context

The instructions a coding agent must read before working here have lived in `CLAUDE.md` since
the first commit. [ADR-0036](0036-claude-md-is-local-only.md) made that file local-only and
[ADR-0037](0037-claude-md-is-tracked-and-maintained.md) reversed it within hours, because a
hidden file drifts unseen while a tracked one changes through review. Neither decision was
about the *name*.

The name has become wrong in one specific way: it says which tool reads the file, and the file
does not contain anything tool-specific. Every rule in it — verify against upstream sources,
one branch per task, an ADR for every decision, do not append findings to a frozen ADR, name
the machine beside a measurement — is about this repository. `AGENTS.md` is the name that has
since become the common convention for exactly this file.

## Forces

**A file named after one tool invites the assumption that another tool needs its own copy.**
That is the failure ADR-0037 already argued about in a different form: two copies of a rule
drift apart, and the one nobody re-reads is the one that goes stale.

**But Claude Code loads `CLAUDE.md` by name.** A plain rename would leave the agent that this
project is actually developed with reading nothing at all, which is worse than a badly named
file. Whether that agent also loads `AGENTS.md` on its own is not something this repository
should depend on.

**Seven accepted ADRs cite `CLAUDE.md` by name, and their bodies are frozen.** ADR-0015,
ADR-0025, ADR-0026, ADR-0036, ADR-0037, ADR-0039 and ADR-0043 all mention it, and the
project's own rule is that an accepted ADR's argument is not edited afterwards. So the old name
has to keep resolving to something, permanently — the citations cannot be corrected, and
should not be.

## Decision

1. **The guidance lives in `AGENTS.md`.** It is the same file, renamed with `git mv` so the
   history follows it, and it keeps every rule it had.
2. **`CLAUDE.md` stays as a pointer**, and carries no guidance of its own. It states in its
   first line that `AGENTS.md` must be read in full, and it also contains an `@AGENTS.md`
   import line, so that a tool which resolves imports inlines the content and a tool which
   does not is still told, in plain words, to go and read it.
3. **Nothing is copied into `CLAUDE.md`, ever** — not a summary, not the parts that seem most
   important. A pointer that starts summarising is a second copy.
4. **The ADRs that cite the old name are left alone.** The pointer file is what keeps those
   citations honest.

## Consequences

**The name now describes the job, and any agent arrives at one file.** A contributor using a
different tool reads `AGENTS.md` directly; a session in Claude Code reaches it through
`CLAUDE.md`. There is one text and one place to change it.

**The pointer is load-bearing and must not be tidied away.** Deleting `CLAUDE.md` as
"redundant" would silently stop Claude Code from loading any guidance at all, and would break
seven ADR citations at the same time. Neither failure is visible in a build: `build/check-docs.py`
would still pass, because nothing links *to* `CLAUDE.md` by path.

**The import line is a convenience, not the mechanism.** Whether `@AGENTS.md` is resolved was
not verified against tool documentation when this was written, and deliberately so: the
imperative sentence above it is what the decision relies on, and it works whichever way the
import behaves. If a future session confirms the behaviour either way, that belongs in
`docs/tasks/`, not here.

**This is the third decision about one file**, after ADR-0036 and ADR-0037. That is worth
noticing rather than hiding: the first two were about whether it is visible, and this one is
about what it is called. None of them changed a word of what it says.
