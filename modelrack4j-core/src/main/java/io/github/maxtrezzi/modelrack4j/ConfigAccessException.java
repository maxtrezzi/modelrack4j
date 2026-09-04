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

/**
 * Thrown when a configuration layer cannot be read or written — the file is missing, the
 * directory is not writable, the disk is full, the database is unreachable.
 *
 * <p>The configuration itself is not in question: nothing was found to be wrong with the
 * text, because the text could not be reached. That is the whole difference from
 * {@link ConfigValidationException}, and it is the difference an application needs when it
 * turns these failures into an answer for someone else. A caller who sends invalid text has
 * to change the text; a caller who meets a full disk has to try again later, and telling
 * them their configuration is invalid is untrue.
 *
 * <p>It is <strong>not</strong> a subclass of {@link ConfigValidationException}. Catching one
 * never catches the other, so the two cases cannot be confused by accident.
 *
 * <p>Where it comes from: reading a layer, at {@code build()}, at {@link LlmRegistry#reload()}
 * and whenever a source's {@code text()} is called; and writing one, from
 * {@link LlmRegistry#store(WritableConfigSource, String)} and
 * {@link LlmRegistry#storeIfUnchanged(WritableConfigSource, String, String)}. A failed store
 * still leaves the previous configuration live, exactly as a rejected one does.
 */
public final class ConfigAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception describing which layer could not be reached, and why.
     *
     * @param message the layer, and what went wrong with it
     */
    public ConfigAccessException(String message) {
        super(message);
    }

    /**
     * Creates an exception describing which layer could not be reached, preserving the
     * underlying failure.
     *
     * @param message the layer, and what went wrong with it
     * @param cause the underlying failure, typically an {@link java.io.IOException}
     */
    public ConfigAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
