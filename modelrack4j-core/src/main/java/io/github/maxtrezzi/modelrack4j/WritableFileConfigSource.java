/*
 * Copyright 2026 maxtrezzi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.maxtrezzi.modelrack4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A file layer that can also be written. Backs {@link ConfigSource#ofWritableFile(Path)}.
 *
 * @implNote The write goes to a temporary file in the <strong>same directory</strong> and is
 *     then moved onto the target. Two things need that, not one. A reader that catches a
 *     partly written file sees a broken layer, and the watcher is exactly such a reader
 *     (ADR-0013). And the staged file has to sit beside the real one so that an
 *     {@code include "sibling.conf"} inside it resolves against the same directory when
 *     the new text is validated, which is what lets validation see what the committed
 *     file will actually mean.
 */
record WritableFileConfigSource(Path file) implements WritableConfigSource, FileBacked {

    private static final Logger log = LoggerFactory.getLogger(WritableFileConfigSource.class);

    /** Prefix for the staged file, so an interrupted write is recognisable in a directory. */
    private static final String STAGE_PREFIX = ".modelrack4j-staged-";

    /**
     * An {@code include} directive at the start of a line. Deliberately lexical:
     * {@link #requireIncludeCanBeValidated(String)} refuses the whole combination rather than
     * working out whether this particular include would find the same file both ways.
     */
    private static final Pattern INCLUDE = Pattern.compile("(?m)^\\s*include\\s");

    /**
     * A file written beside its destination and waiting to be moved onto it.
     *
     * @param path the staged file
     * @param destination the path it will replace, resolved once
     * @implNote The destination travels with the staged file rather than being resolved again
     *     at commit time. Resolving twice lets a symbolic link move in between — which is
     *     exactly what ADR-0024 exists for, and does not wait for this registry's lock — and
     *     the staged file would then be written beside one directory and moved onto another,
     *     carrying permissions copied from a file it no longer replaces.
     */
    record StagedFile(Path path, Path destination) {

        StagedFile {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(destination, "destination");
        }
    }

    WritableFileConfigSource {
        Objects.requireNonNull(file, "file");
    }

    @Override
    public String id() {
        return file.toAbsolutePath().normalize().toString();
    }

    @Override
    public String text() {
        return FileConfigSource.read(file);
    }

    @Override
    public void write(String text) {
        Objects.requireNonNull(text, "text");
        StagedFile prepared = stage(text);
        boolean committed = false;
        try {
            commitStaged(prepared);
            committed = true;
        } finally {
            // A finally rather than a catch: an Error must not leave the staged file behind
            // either, and there is nothing here that wants to see the failure.
            if (!committed) {
                discardStaged(prepared);
            }
        }
    }

    /**
     * Writes {@code text} to a new file beside the target and returns it.
     *
     * @param text the text the target should end up holding
     * @return the staged file, which the caller must either commit or discard
     * @throws ConfigAccessException if the staged file cannot be written
     */
    StagedFile stage(String text) {
        Path destination = destination();
        Path directory = destination.getParent();
        if (directory == null) {
            throw new ConfigAccessException(
                    "Configuration file has no parent directory to write beside: " + file);
        }
        Path staged = null;
        try {
            staged = Files.createTempFile(directory, STAGE_PREFIX, ".conf");
            Files.writeString(staged, text, StandardCharsets.UTF_8);
            copyPermissions(destination, staged);
            StagedFile prepared = new StagedFile(staged, destination);
            staged = null;   // handed over: the caller discards it from here on
            return prepared;
        } catch (IOException e) {
            // e rather than e.getMessage(): an IOException over a path usually carries only
            // that path, and here that path is the staged temporary file — a name the caller
            // has never seen. The type is what names the cause, and the sentence before it
            // says what the path is. It does not say the file was removed again: when
            // createTempFile is what failed, which is the read-only directory case, the path
            // in the failure is a candidate name that was never created.
            throw new ConfigAccessException(
                    "Cannot write the configuration " + file + ": the new text is written to"
                            + " a temporary file in " + directory + " and then moved onto the"
                            + " target, so storing needs that directory to be writable, not"
                            + " only the file. The failure below names that temporary file"
                            + " rather than your configuration: " + e, e);
        } finally {
            // Reached only when the file was created and then not handed over — a failing
            // write, most often a full disk. The caller never receives the path in that
            // case, so nothing else could ever remove it and one hidden file would be left
            // beside the configuration per failed store. A finally rather than a catch, so
            // an Error cleans up too.
            if (staged != null) {
                discardStaged(new StagedFile(staged, destination));
            }
        }
    }

    /**
     * Refuses a text that contains an {@code include} when the staged file used to validate it
     * would not sit in the directory the layer is parsed through.
     *
     * <p>Only a caller that <em>validates</em> the staged file needs this. A plain
     * {@link #write(String)} replaces the file and validates nothing, so it does not ask.
     *
     * @param text the proposed new text
     * @throws ConfigValidationException if the include would be validated against one
     *     directory and read against another
     * @implNote {@code include "sibling.conf"} resolves relative to the file holding the line,
     *     and a symbolic link makes that two different files: measured with
     *     {@code config-1.4.9}, parsing through the link finds the sibling next to the
     *     <em>link</em>, while parsing the staged file beside the link's target finds the
     *     sibling next to the <em>target</em>. Validating against one and then running on the
     *     other would approve a configuration nobody ever gets, so this combination is
     *     refused. It reaches further than a symbolic link on the file itself — a link
     *     anywhere in the path has the same effect, which is why the two parent directories
     *     are compared rather than the file being tested.
     */
    void requireIncludeCanBeValidated(String text) {
        if (!INCLUDE.matcher(text).find()) {
            return;
        }
        Path staging = destination().getParent();
        Path readThrough = file.toAbsolutePath().normalize().getParent();
        if (staging == null || staging.equals(readThrough)) {
            return;
        }
        throw new ConfigValidationException("The layer " + file + " is reached through a"
                + " symbolic link — the file itself, or a directory on the way to it — and the"
                + " text being stored contains an include. An include is resolved next to the"
                + " file that holds it, so it would be validated in " + staging + " and then"
                + " read in " + readThrough + ", which are not the same directory. Store this"
                + " layer without an include, or point it at the real file rather than at the"
                + " link.");
    }

    /**
     * The path a staged file is written beside and then moved onto: the target with any
     * symbolic link followed.
     *
     * @implNote Writing to the link itself would <em>replace</em> it with an ordinary file.
     *     That silently destroys the arrangement ADR-0024 exists for — a Kubernetes ConfigMap
     *     mounts the configuration as a link and swaps it — and leaves the data the link
     *     pointed at behind, still holding the old content. Following the link keeps the link
     *     a link. Where the link's target is read-only, as a ConfigMap's is, the write fails
     *     and the store rolls back, which is the honest outcome.
     */
    private Path destination() {
        try {
            // NOFOLLOW_LINKS, so a link that resolves to nothing still counts as something
            // being there. Plain Files.exists follows the link and answers false for a
            // broken or looping one, which would send both cases down the branch below
            // without ever entering this try — and the catch would then be unreachable.
            return Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                    ? file.toRealPath()
                    : file.toAbsolutePath().normalize();
        } catch (IOException cannotResolve) {
            // Something is at the path but it cannot be followed to a real file: a symbolic
            // link that points at itself or around a cycle, or one whose target sits behind
            // a directory this process may not traverse. The unresolved path is the honest
            // fallback — it is what a write with no link would have used, and the move then
            // fails naming the real problem rather than this one.
            log.debug("modelrack4j could not resolve {} to a real path: {}",
                    file, cannotResolve.toString());
            return file.toAbsolutePath().normalize();
        }
    }

    /**
     * Gives the staged file the permissions the file it will replace already has.
     *
     * @implNote {@code createTempFile} makes a file only its owner can read, and a move
     *     carries that onto the target. Without this, one write silently turns a
     *     world-readable configuration into an owner-only one — measured as
     *     {@code rw-r--r--} becoming {@code rw-------}.
     */
    private static void copyPermissions(Path from, Path to) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(from);
            Files.setPosixFilePermissions(to, permissions);
        } catch (UnsupportedOperationException notPosix) {
            // Windows: there is nothing to carry over, and the move keeps the ACL anyway.
        } catch (IOException e) {
            // The file may simply not exist yet. The staged file then keeps the restrictive
            // permissions createTempFile gave it, which errs the safe way for configuration.
            log.debug("modelrack4j could not copy permissions from {}: {}", from, e.toString());
        }
    }

    /**
     * Names the file a write replaces, adding the resolved path when it is a different one.
     *
     * @param destination the path the staged file is moved onto
     * @return the configured path, and the path it resolves to when the two differ
     * @implNote A write follows symbolic links instead of replacing them (see
     *     {@link #destination()}), so the file that failed is not always the file the layer
     *     was configured with. Naming only the configured path sends the reader to look at
     *     the permissions of the link, in exactly the deployment where a failure here is
     *     expected: a target mounted read-only, which is what ADR-0024 exists for. It says
     *     "resolved to" rather than naming a symbolic link, because the link can be any
     *     directory on the way and not the file itself. It resolves nothing of its own: the
     *     path it is given is the one {@link #stage(String)} already worked out and carried
     *     on the {@code StagedFile}, and resolving again here could name a path the failed
     *     move never touched.
     */
    private String describeReplacedFile(Path destination) {
        return destination.equals(file.toAbsolutePath().normalize())
                ? file.toString()
                : file + " (resolved to " + destination + ", which is the file a write"
                        + " replaces)";
    }

    /**
     * Moves a staged file onto the destination it was prepared for.
     *
     * @param prepared the file returned by {@link #stage(String)}
     * @throws ConfigAccessException if the move fails
     */
    void commitStaged(StagedFile prepared) {
        try {
            try {
                Files.move(prepared.path(), prepared.destination(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                // Some filesystems cannot promise it. A replacing move is still one call and
                // still better than truncating the target and writing into it.
                Files.move(prepared.path(), prepared.destination(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ConfigAccessException(
                    "Cannot replace the configuration file " + describeReplacedFile(prepared.destination())
                            + ": " + e, e);
        }
    }

    /** Removes a staged file that will not be committed. Never throws. */
    void discardStaged(StagedFile prepared) {
        try {
            Files.deleteIfExists(prepared.path());
        } catch (IOException e) {
            // Nothing useful to do: the store already failed, and a leftover staged file is
            // inert — it is not the configured path, so nothing reads it. Logged rather than
            // thrown, because throwing here would replace the real failure with this one.
            log.warn("modelrack4j could not remove the staged file {}: {}",
                    prepared.path(), e.toString());
        }
    }
}
