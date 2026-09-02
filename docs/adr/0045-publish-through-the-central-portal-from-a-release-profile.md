# ADR-0045: Publish through the Central Portal, from a `release` profile, with the last step manual

- **Status:** Accepted
- **Date:** 2026-09-02
- **Supersedes:** —
- **Amends:** —

## Context

M6 was named in `docs/tasks/milestones.md` with no entry of its own, because it was
unscheduled: its trigger was the library proving itself in the owner's first real project,
not a date ([ADR-0034](0034-the-repository-is-public-before-it-is-released.md) draws the same
line between *public* and *released*). The trigger fired on 2026-09-02.

The account half was already done, on 2026-08-30/31, and none of it is visible from the
repository: a Central Portal account, the namespace `io.github.maxtrezzi` verified, an
RSA 4096 signing key published to two keyservers, and a user token in `~/.m2/settings.xml`.
What this ADR settles is the half that *is* in the repository — how the build signs, what it
uploads, and what stops it uploading the rest.

Three constraints were fixed before the question was asked:

- **The repository is public** ([ADR-0034](0034-the-repository-is-public-before-it-is-released.md)),
  so no credential and no passphrase may live in a tracked file.
- **An ordinary build must keep working for someone with no signing key.** CI runs the build
  on three JDKs ([ADR-0026](0026-ci-matrix-is-floor-dev-jdk-and-current-lts.md)) with no
  secrets, and a contributor runs `mvn verify` with none either.
- **`modelrack4j-examples` is not a published artifact.** Its POM says so in its own
  description, and until now `maven.deploy.skip` made that true.

## Forces

**The Portal is the only route left, and it costs a Maven version.** The OSSRH route
(`oss.sonatype.org` with `nexus-staging-maven-plugin`) is retired, and a namespace created
today exists only on the Central Portal. `central-publishing-maven-plugin` declares
`requiredMavenVersion` 3.9.2, which Maven enforces; this project's floor is 3.8.7, and the
machine's `/usr/bin/mvn` is exactly 3.8.7. Raising the project-wide floor to 3.9.2 would make
every contributor pay for a constraint only a release has.

**Signing in the default lifecycle would break the build for everyone else.**
`maven-gpg-plugin` binds `sign` to `verify`, which is inside `mvn install`. Left unguarded it
asks every contributor, and every CI job, for a key they do not have.

**A published component is permanent.** Central allows no modification and no deletion of a
released component. `autoPublish=true` turns `mvn deploy` into that permanent act with no
step in between; `autoPublish=false` uploads a deployment, lets Central validate it, and
stops — the deployment can still be dropped from the Portal.

**Releasing from CI or from the machine.** A GitHub Actions workflow with the key and the
token as secrets makes releases reproducible and removes the machine from the loop. It is
also a second unfamiliar thing to get right on the day of the first release, and it moves a
signing key that currently exists in one place into a second one.

## Decision

1. **Publish through the Central Portal**, with
   `org.sonatype.central:central-publishing-maven-plugin` (0.11.0 at this date),
   `<extensions>true</extensions>` and `<publishingServerId>central</publishingServerId>`.
2. **Everything that signs or publishes lives in a `release` profile on the parent POM.**
   `mvn clean install`, `mvn verify` and every CI job are unchanged and need no key.
3. **`autoPublish` is `false`.** The build uploads a deployment and Central validates it; a
   human presses the last button on the Portal.
4. **The Maven floor rises inside the profile only**: a profile-scoped
   `requireMavenVersion` of `[3.9.2,)`. The build-wide floor stays `[3.8.7,)`. The enforcer
   fails earlier than the plugin and names the version that is missing.
5. **Signing uses `maven-gpg-plugin` (3.2.8 at this date), `sign` bound to `verify`.** The
   passphrase comes from the GnuPG agent or from `MAVEN_GPG_PASSPHRASE`, and from no file.
6. **The first release runs from the owner's machine, not from CI.** The key is there,
   `autoPublish=false` already requires a human at the Portal, and a release workflow can be
   added later without changing any of the above.

## Consequences

**`maven.deploy.skip` stops protecting `modelrack4j-examples`, and this is the trap in the
decision.** The publishing plugin ships a lifecycle participant that *removes*
`maven-deploy-plugin` from the build and injects its own `publish` goal in its place. Nothing
then reads `maven.deploy.skip` — a string search over every class in the plugin jar finds zero
occurrences of it. Under `-Prelease` the property is dead, and the module it protects would go
to Maven Central. `skipPublishing` in that module is what replaces it. **Both properties stay,
because they cover different builds**, and removing either one publishes the examples by
accident. The comment in that POM says so; a future reader tidying "a redundant property" is
the failure this paragraph exists to prevent.

**A dry run cannot use `skipPublishing`, and it must block two URLs rather than one.** The
obvious `-DskipPublishing=true` is not a "build the bundle but do not upload" switch: the flag
is evaluated **per artifact**, inside the filter that decides what enters the bundle, so it
produces an empty bundle. An assertion that the bundle holds no `modelrack4j-examples` path
would then pass while proving nothing. For a release version the bundle is written before the
upload, so the honest dry run points `centralBaseUrl` at an unreachable address: the zip is
produced, the upload fails, and the bundle can be inspected.

**A `-SNAPSHOT` version does not take that path at all.** The plugin branches on the version:
a snapshot is deployed straight to `https://central.sonatype.com/repository/maven-snapshots/`
as an ordinary Maven repository transfer, with no bundle and no deployment on the Portal — and
that address comes from `centralSnapshotsUrl`, a *different* parameter. Blocking only
`centralBaseUrl` while the version is still a snapshot therefore blocks nothing, which is how
this project's first dry run reached Central for real. It was refused with **403**: snapshot
publishing is enabled per namespace and `io.github.maxtrezzi` does not have it. That also
settles, empirically, the middle path the M6 dossier had left open — publishing
`0.1.0-SNAPSHOT` to Central so a consumer can depend on it is not available today without
enabling it on the Portal first.

**Every child POM needed four inheritance attributes it did not have, and they do not all go
in the same place.** Central requires a `url` and an `scm`, and Maven's default inheritance
appends the module directory to both, so `modelrack4j-core` would have published a `url`
ending in `/modelrack4j-core` — a 404 in a required field. The fix is
`child.project.url.inherit.append.path="false"` plus the three
`child.scm.*.inherit.append.path="false"`.

**The first three of those belong on `<scm>`; the fourth belongs on `<project>`, not on
`<url>`.** The XSD settles it: `child.project.url.inherit.append.path` is declared as an
attribute of the `Model` type, while the `child.scm.*` ones are declared on `Scm`. Written on
`<url>` the attribute is accepted without complaint and has no effect — verified here, first
by the effective POM of `modelrack4j-core` still ending in `/modelrack4j-core`, then in a
two-POM project outside this repository that reproduces it in isolation. Check this with
`mvn -pl <module> help:effective-pom`, not by reading the parent: it is invisible in a normal
build and shows up only in an effective or a deployed POM.

**A release needs a Maven that an ordinary shell may not give it.** 3.9.16 is installed
through SDKMAN, which is wired in from `.bashrc`: an interactive shell resolves `mvn` to
3.9.16, a non-interactive one still resolves it to `/usr/bin/mvn` 3.8.7. Any scripted release
must source `~/.sdkman/bin/sdkman-init.sh` or use an absolute path. The profile-scoped
enforcer rule turns that into a clear failure rather than a confusing one.

**Nothing published can be taken back.** That is the cost the project accepts by releasing at
all, and the reason for `autoPublish=false` and for the trigger that held M6 until now. It
also means the version number in a release is a promise: the project is `0.x` and its
CHANGELOG reserves the right to break in a minor, so the first releases record what the
consuming application demands rather than pretending the API has settled.
