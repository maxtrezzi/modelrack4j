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

import java.nio.file.Path;
import java.util.Objects;

/**
 * A layer whose text lives in a real file, and which therefore must be parsed through that
 * file rather than through its text.
 *
 * @implNote {@code include "sibling.conf"} resolves relative to the file that contains the
 *     line, and only {@code parseFile} knows which file that is; handing the same bytes to
 *     {@code parseString} moves the includer to the process's working directory and the
 *     classpath, where an allow-missing include quietly finds nothing (ADR-0042). Validating
 *     a text about to be stored needs the same treatment, against the not-yet-committed file.
 *     <p>Only {@link Layer#of(ConfigSource)} reads this marker, and it is {@code sealed}
 *     because that set is closed and internal: the three records permitted below are the
 *     only layers this library can parse through a file or watch, and an application's own
 *     source cannot join them (ADR-0051).
 */
sealed interface FileBacked extends ConfigSource
        permits FileConfigSource, StagedFileSource, WritableFileConfigSource {

    /** @return the file to parse, absolute and normalised by {@link #stored(Path)} */
    Path file();

    /**
     * Prepares a caller's path for storing, which every implementation does in its
     * constructor.
     *
     * @param file the path as the caller gave it
     * @return it, absolute and normalised
     * @throws NullPointerException if it is null
     * @implNote Both halves are load-bearing. <strong>Absolute</strong>, because
     *     {@code Path.of("app.conf")} has no parent and {@code parseFile} then has no
     *     directory against which to resolve {@code include "sibling.conf"} — the included
     *     file is lost, and silently, since an include is allow-missing.
     *     <strong>Normalised</strong>, so that two spellings of one file, {@code a.conf} and
     *     {@code ./a.conf}, are one source: doing this in {@code id()} alone left the two
     *     reporting one identity while comparing unequal, so a {@code store()} through the
     *     other spelling was refused against a list that looked like it contained the layer.
     *     <p>Normalising is lexical, so a {@code ..} that follows a symbolic link names a
     *     different file from the one the operating system would open. That is accepted:
     *     resolving the link instead is exactly what ADR-0024 forbids, because the watcher
     *     has to register on the link rather than on its target, and dropping the
     *     normalisation would make two spellings two sources again.
     */
    static Path stored(Path file) {
        return Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }
}
