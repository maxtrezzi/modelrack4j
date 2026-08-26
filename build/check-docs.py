#!/usr/bin/env python3
"""Consistency checks for the tracked documentation.

Every check here exists because the corresponding mistake was actually made:

  1. An ADR's own Status line and its row in the index drifted apart. Four ADRs recorded an
     amendment in the index that their own header never got. A hand-rolled version of this
     check missed it for months by comparing only the text before the em-dash — so this one
     compares the WHOLE string, and that is the point of it.
  2. An ADR file existed with no index row, or a row pointed at no file.
  3. A link or anchor resolved on the author's machine but not in a fresh clone, because the
     checker used os.path.exists and the target was git-ignored. Everything here is resolved
     against `git ls-files` instead: what a stranger actually gets.
  4. A status line said "per ADR-0022" instead of "amended by ADR-0022", so it recorded the
     amendment in prose a reader scanning for one would miss. The shape is checked.
  5. An amendment pointed one way only. If A's status says it was amended by B, then B's
     `Amends` header has to name A, and the reverse — otherwise half the trail is invisible
     from whichever end you start at.

Run: python3 build/check-docs.py     (exit 0 clean, 1 with findings)
"""
import os
import re
import subprocess
import sys

ADR_DIR = "docs/adr"
TEMPLATE = "0000"

# The four shapes documented in docs/adr/README.md. The amending verb may be "amended",
# "widened" or "narrowed" — ADR-0008 has read "swap scope widened by ADR-0012" since the
# first commit, and that is more precise than the generic word, not a deviation from it.
STATUS_SHAPE = re.compile(
    r"^(Proposed"
    r"|Accepted"
    r"|Accepted — .+ (?:amended|widened|narrowed) by ADR-\d{4}"
    r"|Superseded by ADR-\d{4})$"
)


def tracked_files():
    """What a fresh clone contains — never the working tree."""
    out = subprocess.run(["git", "ls-files"], capture_output=True, text=True, check=True)
    return set(out.stdout.split())


def strip_links(text):
    return re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)


def normalise(text):
    return re.sub(r"\s+", " ", strip_links(text)).strip().rstrip(".")


def github_anchor(heading):
    """GitHub's slug: drop punctuation, then EVERY space becomes its own hyphen."""
    t = re.sub(r"`([^`]*)`", r"\1", strip_links(heading))
    t = re.sub(r"[*_]", "", t).strip().lower()
    t = "".join(c for c in t if c.isalnum() or c in " -_")
    return t.replace(" ", "-")


def adr_header(path, field):
    with open(path, encoding="utf-8") as fh:
        head = fh.read(1500)
    m = re.search(rf"^- \*\*{field}:\*\* (.+)$", head, re.M)
    return normalise(m.group(1)) if m else None


def adr_status(path):
    with open(path, encoding="utf-8") as fh:
        head = fh.read(1200)
    m = re.search(r"^- \*\*Status:\*\* (.+)$", head, re.M)
    return normalise(m.group(1)) if m else None


def index_rows():
    rows = {}
    with open(f"{ADR_DIR}/README.md", encoding="utf-8") as fh:
        for line in fh:
            m = re.match(r"\|\s*\[(\d{4})\]\([^)]*\)\s*\|([^|]*)\|([^|]*)\|", line)
            if m:
                rows[m.group(1)] = normalise(m.group(3))
    return rows


def check_adrs(problems):
    files = {}
    for name in sorted(os.listdir(ADR_DIR)):
        m = re.match(r"(\d{4})-.*\.md$", name)
        if m and m.group(1) != TEMPLATE:
            files[m.group(1)] = f"{ADR_DIR}/{name}"
    rows = index_rows()

    for num in sorted(set(files) - set(rows)):
        problems.append(f"ADR-{num} has a file but no row in {ADR_DIR}/README.md")
    for num in sorted(set(rows) - set(files)):
        problems.append(f"ADR-{num} has an index row but no file")

    for num in sorted(set(files) & set(rows)):
        status, row = adr_status(files[num]), rows[num]
        if status is None:
            problems.append(f"ADR-{num} has no '- **Status:**' line")
        elif status != row:
            problems.append(
                f"ADR-{num} status drift\n"
                f"      file  : {status}\n"
                f"      index : {row}\n"
                f"      (the ADR's own header is authoritative — fix the index to match, "
                f"or add the missing pointer to the header)"
            )

    for num in sorted(set(files) & set(rows)):
        status = adr_status(files[num])
        if status and not STATUS_SHAPE.match(status):
            problems.append(
                f"ADR-{num} status does not match a documented shape: {status!r}\n"
                f"      (see the Status values table in {ADR_DIR}/README.md)"
            )

    # An amendment has two ends. Both must name the other, or the trail is one-way.
    def cited(text):
        return set(re.findall(r"ADR-(\d{4})", text or ""))

    for num, path in sorted(files.items()):
        status = adr_status(path) or ""
        superseded = "uperseded" in status
        for target in cited(status):
            if target not in files:
                problems.append(f"ADR-{num} status names ADR-{target}, which has no file")
                continue
            field = "Supersedes" if superseded else "Amends"
            if num not in cited(adr_header(files[target], field)):
                problems.append(
                    f"ADR-{num} status says it was {'superseded' if superseded else 'amended'} "
                    f"by ADR-{target}, but ADR-{target}'s {field} header does not name ADR-{num}"
                )
        for field, verb in (("Amends", "amends"), ("Supersedes", "supersedes")):
            for target in cited(adr_header(path, field)):
                if target not in files:
                    problems.append(f"ADR-{num} {field} ADR-{target}, which has no file")
                    continue
                if num not in cited(adr_status(files[target])):
                    problems.append(
                        f"ADR-{num} {verb} ADR-{target}, but ADR-{target}'s status "
                        f"does not record it"
                    )

    numbers = sorted(int(n) for n in files)
    if numbers:
        gaps = [n for n in range(numbers[0], numbers[-1] + 1) if n not in numbers]
        if gaps:
            problems.append(f"ADR numbering gaps: {gaps}")
    return len(files)


def check_links(problems, tracked):
    docs = sorted(f for f in tracked if f.endswith(".md"))
    anchors = {}
    for path in docs:
        with open(path, encoding="utf-8") as fh:
            anchors[path] = {
                github_anchor(m.group(2))
                for m in (re.match(r"^(#{1,6})\s+(.*?)\s*$", line) for line in fh)
                if m
            }
    dirs = {os.path.dirname(f) for f in tracked} | {""}

    for path in docs:
        with open(path, encoding="utf-8") as fh:
            body = fh.read()
        # a link inside a code span is literal text, not a link
        spans = [(m.start(), m.end()) for m in re.finditer(r"`[^`]*`", body)]
        for m in re.finditer(r"\[[^\]]*\]\(([^)\s]+)\)", body):
            if any(s <= m.start() < e for s, e in spans):
                continue
            link = m.group(1)
            if link.startswith(("http://", "https://", "mailto:")):
                continue
            rel, _, frag = link.partition("#")
            target = os.path.normpath(os.path.join(os.path.dirname(path), rel)) if rel else path
            if rel and target not in tracked and target not in dirs:
                problems.append(f"{path}: link to untracked target -> {link}")
                continue
            if frag and target.endswith(".md") and frag not in anchors.get(target, set()):
                problems.append(f"{path}: no such anchor -> {link}")
    return len(docs)


def main():
    problems = []
    tracked = tracked_files()
    adr_count = check_adrs(problems)
    doc_count = check_links(problems, tracked)

    print(f"checked {adr_count} ADRs and {doc_count} tracked markdown files")
    if problems:
        print(f"\n{len(problems)} problem(s):\n")
        for p in problems:
            print(f"  - {p}")
        return 1
    print("no problems found")
    return 0


if __name__ == "__main__":
    sys.exit(main())
