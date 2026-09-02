# CLAUDE.md

**The guidance for this repository is in [AGENTS.md](AGENTS.md). Read that file in full
before doing anything here.** It is long on purpose: most of it exists because something went
wrong once, and a session that skips it repeats that.

@AGENTS.md

This file is a pointer, not a second copy. Two copies of a rule drift apart, and this project
has already paid for that once — see
[ADR-0037](docs/adr/0037-claude-md-is-tracked-and-maintained.md). Do not move guidance back
into this file, and do not summarise `AGENTS.md` here.

The guidance was called `CLAUDE.md` until 2026-09-02, when it moved to `AGENTS.md` so that its
name describes the job rather than one tool
([ADR-0046](docs/adr/0046-agent-guidance-lives-in-agents-md.md)). This file stays behind
because Claude Code loads `CLAUDE.md` by name, and because several accepted ADRs cite the old
name and their bodies cannot be edited.
