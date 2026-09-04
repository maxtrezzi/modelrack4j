# ADR-0052: No version token — the expected text is the token

- **Status:** Accepted
- **Date:** 2026-09-04
- **Supersedes:** —
- **Amends:** —

## Context

[ADR-0044](0044-store-a-layer-back-as-text-validated-before-it-is-stored.md) gave two writers a
compare-and-set: `storeIfUnchanged(target, expected, text)` re-reads the layer under the reload
lock, compares it with `expected` character for character, and throws `StaleLayerException`
carrying the layer's current text when they differ.

Putting that method behind an HTTP `PUT` raised the question this ADR answers. The obvious
shape for a conditional write over HTTP is an `ETag` and an `If-Match` header: a short opaque
token, not a document. `storeIfUnchanged` takes a whole document instead. Does the library need
a smaller way to say "unchanged" — `String version()` on `ConfigSource`, with a
`storeIfVersion(target, version, text)` beside the existing method, or an overload that takes a
digest?

The decision is dated by the artifact, not by the code: `0.1.0` is published and permanent, and
a method added now can never be removed. That raises the bar for a second way of saying
something the API can already say.

## Forces

**The ETag pattern already works, unchanged, on the current signature.** The application that
serves the `PUT` is the one that read the layer in the first place, so it holds the text and can
derive the token itself:

```java
String current = layer.text();                        // what the client last saw
if (!etagOf(current).equals(request.header("If-Match"))) {
    return status(412);                               // precondition failed
}
registry.storeIfUnchanged(layer, current, newText);    // the library closes the race
```

The client ships an `ETag`; nothing ships a document over the wire. The read the server does is
a read it needs anyway, and the race between that read and the write is closed inside
`storeIfUnchanged`, under the reload lock, against a fresh `text()` — which no header
comparison in the application could do for itself.

**A token is a second way to say "unchanged", and two ways can disagree.** `version()` would be
derived from the same text the comparison already uses, so it adds no information. What it adds
is a failure mode: an implementation whose `version()` and `text()` fall out of step —
a cached digest, a timestamp that does not change within its resolution, a hash computed over
normalised text — reports "unchanged" for a layer that moved. The current comparison cannot
drift from the thing it compares, because it *is* the thing.

**Against that: a token is natural over a network, and cheap to hold.** A client that wants to
poll, cache, or hold a layer's identity across requests can keep 32 characters instead of a
document, and `version()` would put that in the library rather than in every application that
wants it. This is a real argument and it is the reason the question was raised. It loses on the
permanence above: the pattern is implementable today in four lines, and a published method
cannot be withdrawn if it turns out to be the wrong four lines.

**Byte-exactness is not negotiable either way.** ADR-0044 settled that a reformatted or
re-commented layer *is* a layer that moved, and a digest changes with it. Nothing here reopens
that.

## Decision

**The library gains no version token.** `ConfigSource` keeps `id()` and `text()`;
`storeIfUnchanged(target, expected, text)` keeps its signature; no `version()`, no
`storeIfVersion`, no digest-taking overload.

An application that wants an `ETag` derives it from `text()` and keeps the digest to itself. The
library's business is the compare-and-set that closes the race, not the wire format of the
condition.

## Consequences

**A conditional store costs one `text()` read**, which for a file layer is I/O and for a
database layer is a query. That read is the one the caller already made to produce the version
it is proposing a change to, so in the shape this was raised for it is not an extra cost. An
application that discards its read and calls `storeIfUnchanged` from a bare token would pay for
it — and cannot, which is the point.

**`StaleLayerException.current()` stays the recovery path.** A caller that loses the race is
handed the text that beat it, in the exception, and does not have to fetch it again to rebase.
A token-based API would have handed back a token and forced that second fetch.

**Any future token arrives as a new ADR, not as a convenience.** If a consumer appears that
genuinely cannot hold the text — a client library talking to a remote configuration service, say
— the argument changes and this decision can be revisited. What must not happen is a
`version()` added quietly beside `text()` because it looked tidy: that is the disagreement this
ADR is refusing.

**Nothing changes in the code.** This ADR records a decision not to act, which is worth writing
down precisely because the API it protects looks incomplete next to an `ETag`. The next reader
to notice that should find this file rather than the idea.
