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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A source that reads a file on every call. Backs {@link ConfigSource#ofFile(Path)}.
 *
 * @implNote The charset is passed explicitly. On Java 17 the platform default still follows
 *     the locale — JEP 400 made UTF-8 the default only in 18 — so a configuration file with
 *     an accented comment would decode differently on a developer machine and on a server.
 */
record FileConfigSource(Path file) implements FileBacked {

    FileConfigSource {
        Objects.requireNonNull(file, "file");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote The absolute, normalised path, so that two spellings of one file — {@code
     *     a.conf} and {@code ./a.conf} — are recognised as the duplicate layer they are
     *     rather than passing as two. It also makes the path in an error message findable
     *     without knowing the working directory the application was started from.
     */
    @Override
    public String id() {
        return file.toAbsolutePath().normalize().toString();
    }

    @Override
    public String text() {
        return read(file);
    }

    /**
     * Reads a configuration file as UTF-8, shared with {@link WritableFileConfigSource}.
     *
     * @param file the file to read
     * @return its text
     * @throws ConfigAccessException if it is missing or cannot be read
     */
    static String read(Path file) {
        // Checked rather than left to readString so the message names the file and the
        // reason, instead of surfacing a bare NoSuchFileException from inside the loader.
        if (!Files.isReadable(file)) {
            throw new ConfigAccessException(
                    "Configuration file does not exist or is not readable: " + file);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // e rather than e.getMessage(): the message of an IOException over a path is
            // often just that path again, so the type is the only part that says what
            // happened. "…: java.nio.file.AccessDeniedException: /etc/app.conf" beats
            // "…: /etc/app.conf".
            throw new ConfigAccessException(
                    "Cannot read configuration file " + file + ": " + e, e);
        }
    }
}
