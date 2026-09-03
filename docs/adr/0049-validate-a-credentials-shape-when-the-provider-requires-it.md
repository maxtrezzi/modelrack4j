# ADR-0049: Validate a credential's shape, never its content

- **Status:** Accepted
- **Date:** 2026-09-03
- **Supersedes:** —
- **Amends:** —

## Context

The manual tells an application to catch `dev.langchain4j.exception.LangChain4jException` and
promises that the handling then survives a provider swap. That claim holds for what a failing
*call* throws — all four providers throw beneath it.

GLM breaks it before the call. `langchain4j-community-zhipu-ai` does not send the API key. It
splits the key on `.`, treats the first part as an id and the second as an HMAC secret, and
signs a JWT with them. So the key is *parsed and used* while the first request is assembled,
and a key of the wrong shape fails there, inside `AuthorizationUtils.generateToken`, with
nothing in the message about a key.

P25 recorded one such failure. Running it produced three, all before any socket is opened, all
outside the `LangChain4jException` family:

| Key | Failure |
|---|---|
| no `.` at all, or a trailing `.` | `java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1` |
| empty secret half | `java.lang.IllegalArgumentException: Empty key`, from `javax.crypto.spec.SecretKeySpec` |
| secret under 16 bytes | `io.jsonwebtoken.security.SignatureException`, wrapping `WeakKeyException` |

The boundary is exactly 16 bytes: at 15 the JWT library refuses to sign, at 16 the request
reaches the network. That is RFC 7518 section 3.2, which requires an HS256 key at least as long
as the hash output. A key with an empty *id* half is not a local failure — it signs, it is sent,
and the server rejects it as a real `ZhipuAiException`.

An application that maps this library's exceptions onto responses therefore reports a mistyped
credential as an internal fault. That is what needed deciding, and the question it raised is
wider than GLM: the schema deliberately does not validate `model-name`, because upstream's
enums lag the providers and a catalogue of live values rots. Does a key's shape rot the same
way?

## Forces

- **A shape is not a catalogue.** `model-name` is a list of values that exists on a vendor's
  servers and changes without warning. `id.secret` is a format the provider's own code requires
  unconditionally, on every request, with no server involved. The first can only be checked by
  asking someone; the second is a property of code already on the classpath.
- **The current failure carries no diagnosis.** `Index 1 out of bounds for length 1` does not
  name a key, a provider, or a configuration block. Turning it into a
  `ConfigValidationException` that names the block is the whole value of the change, and
  `validate()` is where ADR-0048 put exactly this kind of rule — one core cannot see.
- **Fail-fast is this project's posture** (ADR-0008). A configuration that can never work
  should not survive a reload and wait for the first user request to reveal itself.
- **The 16-byte threshold comes from one dependency further out.** It is enforced by the JWT
  library the provider happens to use, not by the provider or by Zhipu. If the module changed
  JWT library or signing algorithm, the number could move. This is the genuine argument against
  including it, and it is why the alternative below was real.
- **The rejected alternative was to check only the split.** It refuses what breaks in Zhipu's
  own code and leaves the JWT library's rule to documentation — a boundary that is easier to
  defend and stops one line short of useful. The keys people actually mistype are truncated
  ones and placeholders, and a truncated key usually keeps its dot. Checking only the split
  would let the most common real mistake through, into the failure this ADR exists to remove.
- **Nothing that could work is refused, and this does not rest on knowing Zhipu's key format.**
  Both conditions are read off the provider's own code: a key that fails either one cannot
  produce a token, so it could never have completed a call. That is a stronger argument than a
  claim about how long real keys are, which this project has no way to verify from here.
- **Documentation alone was the third option.** It costs nothing and fixes nothing: the
  exception an application receives is unchanged, and the manual already had a reader who
  believed the `LangChain4jException` guarantee.

## Decision

**A provider may validate the *shape* of a credential when its own code requires that shape
unconditionally. It never validates a credential's content, and it never validates a catalogue
of values a vendor controls.**

`GlmProviderFactory.validate()` rejects an `api-key` whose `split("\\.")` yields fewer than two
elements, or whose second element is under 16 UTF-8 bytes. Both throw
`ConfigValidationException` naming the block and the requirement. The measurement is in bytes,
not characters, because that is what the provider signs with.

Two things the check deliberately does **not** do. It does not reject an empty id half, which
is a live-credential question the server answers. And it never puts the key in the message
(ADR-0047) — the length of an already-invalid secret is printed, because it separates a
truncated key from a placeholder, and nothing else is.

`ZhipuAiKeyHandlingTest` characterises the upstream behaviour this rests on, offline, against a
closed local port, with a well-shaped key as the control that proves the other failures happen
earlier.

## Consequences

- A malformed GLM key is refused when the configuration loads, in this library's own exception
  type, naming the block. It no longer reaches an application as an `ArrayIndexOutOfBoundsException`.
- **This is a breaking change for a configuration that could never have worked.** A key that
  fails the check failed on the first request before, and fails at startup now. The CHANGELOG
  reserves that for a `0.x` minor.
- The library now encodes a constant that belongs to a dependency of a dependency. If
  `langchain4j-community-zhipu-ai` changes its JWT library or its algorithm, 16 becomes the
  wrong number. `ZhipuAiKeyHandlingTest` is what would report that, and it is the reason that
  test exists rather than being folded into the factory's own tests. **Do not delete it as a
  test of somebody else's code** — it is the expiry check on this decision.
- `validate()` now has one non-empty body among the four, which is the shape ADR-0048 predicted
  when it kept the method rather than removing it.
- A future provider with a parseable credential has a rule to follow and, more usefully, a
  limit: the question to ask is whether the provider's code needs the shape before it makes a
  call, not whether the credential looks plausible.
