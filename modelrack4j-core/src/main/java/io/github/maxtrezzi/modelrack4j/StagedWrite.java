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
 * A write that has been prepared but not yet made permanent.
 *
 * <p>Splitting a write in two is what lets a new text be validated against what will
 * actually be stored, and then rolled back without a trace if storing it fails. {@link #source()} is what
 * the loader reads during validation; {@link #commit()} makes it the layer's real content;
 * {@link #discard()} throws the preparation away and must be safe to call either way.
 */
interface StagedWrite {

    /** @return the source the loader should read instead of the target while validating */
    ConfigSource source();

    /**
     * Makes the staged text the layer's content.
     *
     * @throws ConfigValidationException if it cannot be stored
     */
    void commit();

    /** Releases whatever the preparation held. Never throws. */
    void discard();

    /**
     * Prepares a write against the target.
     *
     * @implNote A file target stages a real file beside itself, because that is the only way
     *     an {@code include} inside the new text resolves during validation the way it will
     *     resolve afterwards (ADR-0042). Anything else has no directory of its own, so its
     *     staged text is validated as text — which is also exactly how that source's own
     *     {@code text()} would be parsed on the next reload, so validation and reality agree
     *     there too.
     * @param target the layer being written
     * @param text the proposed new text
     * @return the prepared write
     */
    static StagedWrite prepare(WritableConfigSource target, String text) {
        if (target instanceof WritableFileConfigSource fileTarget) {
            return new StagedFileWrite(fileTarget, text);
        }
        return new StagedTextWrite(target, text);
    }
}
