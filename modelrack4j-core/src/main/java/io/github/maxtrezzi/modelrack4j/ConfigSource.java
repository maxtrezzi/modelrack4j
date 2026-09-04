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

/**
 * One layer of configuration: some HOCON text, and a label for it.
 *
 * <p>A layer does not have to be a file. It can be a row in a database, a value fetched from
 * a configuration service, or a string built in memory — anything that can produce text. The
 * library never learns where the text came from, which is why this interface names no file,
 * no path and no URL.
 *
 * <p>Sources are given to {@link LlmRegistry.Builder#sources(java.util.List)} in order,
 * <strong>lowest precedence first</strong>. They are merged and then resolved once
 * (ADR-0007), so a {@code ${VAR}} substitution in one layer can be satisfied by another.
 *
 * @see LlmRegistry.Builder#configFiles(java.util.List) the shorthand for the common case
 */
public interface ConfigSource {

    /**
     * A short, stable label for this layer, used in error messages and in
     * {@link ReloadFailure}.
     *
     * <p><strong>This is a label, not an address.</strong> The library never reads it, parses
     * it or resolves it — it only prints it. Give it whatever helps a reader of a log find
     * the layer again: {@code "base.conf"}, {@code "llm_config#42"}, {@code "tenant-7"}.
     *
     * <p>Because it is logged, it must not contain a secret. A database connection string
     * with a password in it is a poor identifier for exactly this reason.
     *
     * <p>Two sources of one registry must not share an identifier; the builder rejects
     * duplicates, because an ambiguous label makes an error message useless.
     *
     * @return the label, never {@code null} and never blank
     */
    String id();

    /**
     * The HOCON text of this layer.
     *
     * <p><strong>Called again on every reload</strong>, so an implementation that queries a
     * database here behaves correctly, and one that reads its text once and remembers it will
     * never report a change. This is the caching trap that {@link LlmRegistry#get(String)}
     * warns about, in the other direction.
     *
     * <p><strong>A HOCON {@code include} does not work in this text.</strong> An include is
     * resolved relative to the file that contains it, and this text has no file, so it is
     * looked up on the classpath instead. An include that finds nothing is not an error in
     * HOCON: the block simply disappears, with nothing logged. Assemble the whole text
     * before returning it, or use {@link #ofFile(Path)} — a file layer is parsed through the
     * file itself, and is the only layer whose includes resolve the way a reader expects.
     *
     * @return the text, never {@code null}; an empty string is a valid empty layer
     * @throws ConfigAccessException if the text cannot be produced — a missing file, a
     *     database that cannot be reached. The reload is then rejected as a whole and the
     *     previous configuration stays live. Throw this rather than
     *     {@link ConfigValidationException}: nothing is wrong with the text, it could not be
     *     reached.
     */
    String text();

    /**
     * Returns a source holding text that never changes.
     *
     * <p>The text is captured now. A reload re-reads it and always finds the same thing, so
     * this is for a layer that is genuinely fixed, or for a test. A layer that has to be
     * re-read — a file, a database row — needs an implementation whose {@link #text()} goes
     * and looks.
     *
     * @param id the label, not blank
     * @param text the HOCON text
     * @return a source over that text
     * @throws ConfigValidationException if the id is null or blank
     * @throws NullPointerException if the text is null
     */
    static ConfigSource of(String id, String text) {
        return new FixedConfigSource(id, text);
    }

    /**
     * Returns a source that reads a file, on every reload.
     *
     * @param file the file to read, decoded as UTF-8
     * @return a source over that file, identified by its path
     * @throws NullPointerException if the file is null
     */
    static ConfigSource ofFile(Path file) {
        return new FileConfigSource(file);
    }

    /**
     * Returns a source that reads a file and can also write it back.
     *
     * <p>The same layer as {@link #ofFile(Path)}, plus the ability to be named as the target
     * of {@link LlmRegistry#store(WritableConfigSource, String)}. Use it for the layer that
     * holds choices your application's users make; leave the layers you ship read-only.
     *
     * @param file the file to read and write, as UTF-8
     * @return a writable source over that file
     * @throws NullPointerException if the file is null
     */
    static WritableConfigSource ofWritableFile(Path file) {
        return new WritableFileConfigSource(file);
    }
}
