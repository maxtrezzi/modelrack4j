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
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A file layer that can also be written. Backs {@link ConfigSource#ofWritableFile(Path)}.
 *
 * @implNote The write goes to a temporary file in the <strong>same directory</strong> and is
 *     then moved onto the target. Two things need that, not one. A reader that catches a
 *     partly written file sees a broken layer, and the watcher is exactly such a reader
 *     (ADR-0013). And the staged file has to sit beside the real one so that an
 *     {@code include "sibling.conf"} inside it resolves against the same directory when the
 *     edit is validated, which is what lets validation see what the committed file will
 *     actually mean.
 */
record WritableFileConfigSource(Path file) implements WritableConfigSource, FileBacked {

    private static final Logger log = LoggerFactory.getLogger(WritableFileConfigSource.class);

    /** Prefix for the staged file, so an interrupted edit is recognisable in a directory. */
    private static final String STAGE_PREFIX = ".modelrack4j-staged-";

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
        Path staged = stage(text);
        try {
            commitStaged(staged);
        } catch (RuntimeException e) {
            discardStaged(staged);
            throw e;
        }
    }

    /**
     * Writes {@code text} to a new file beside the target and returns it.
     *
     * @param text the text the target should end up holding
     * @return the staged file, which the caller must either commit or discard
     * @throws ConfigValidationException if the staged file cannot be written
     */
    Path stage(String text) {
        Path destination = destination();
        Path directory = destination.getParent();
        if (directory == null) {
            throw new ConfigValidationException(
                    "Configuration file has no parent directory to write beside: " + file);
        }
        try {
            Path staged = Files.createTempFile(directory, STAGE_PREFIX, ".conf");
            Files.writeString(staged, text, StandardCharsets.UTF_8);
            copyPermissions(destination, staged);
            return staged;
        } catch (IOException e) {
            throw new ConfigValidationException(
                    "Cannot write the configuration beside " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * The path the staged file is written beside and then moved onto: the target with any
     * symbolic link followed.
     *
     * @implNote Writing to the link itself would <em>replace</em> it with an ordinary file.
     *     That silently destroys the arrangement ADR-0024 exists for — a Kubernetes ConfigMap
     *     mounts the configuration as a link and swaps it — and leaves the data the link
     *     pointed at behind, still holding the old content. Following the link keeps the link
     *     a link. Where the link's target is read-only, as a ConfigMap's is, the write fails
     *     and the edit rolls back, which is the honest outcome.
     */
    private Path destination() {
        try {
            return Files.exists(file) ? file.toRealPath() : file.toAbsolutePath().normalize();
        } catch (IOException cannotResolve) {
            return file.toAbsolutePath().normalize();
        }
    }

    /**
     * Gives the staged file the permissions the file it will replace already has.
     *
     * @implNote {@code createTempFile} makes a file only its owner can read, and a move
     *     carries that onto the target. Without this, one edit silently turns a
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
     * Moves a staged file onto the target.
     *
     * @param staged the file returned by {@link #stage(String)}
     * @throws ConfigValidationException if the move fails
     */
    void commitStaged(Path staged) {
        Path destination = destination();
        try {
            try {
                Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                // Some filesystems cannot promise it. A replacing move is still one call and
                // still better than truncating the target and writing into it.
                Files.move(staged, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ConfigValidationException(
                    "Cannot replace the configuration file " + file + ": " + e.getMessage(), e);
        }
    }

    /** Removes a staged file that will not be committed. Never throws. */
    void discardStaged(Path staged) {
        try {
            Files.deleteIfExists(staged);
        } catch (IOException e) {
            // Nothing useful to do: the edit already failed, and a leftover staged file is
            // inert — it is not the configured path, so nothing reads it. Logged rather than
            // thrown, because throwing here would replace the real failure with this one.
            log.warn("modelrack4j could not remove the staged file {}: {}",
                    staged, e.toString());
        }
    }
}
