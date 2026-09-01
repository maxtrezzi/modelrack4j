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
 * A staged write to any other layer: nothing is stored until {@link #commit()}, so there is
 * nothing to undo.
 *
 * @implNote Top-level for the same reason as {@link StagedFileWrite}.
 */
final class StagedTextWrite implements StagedWrite {

    private final WritableConfigSource target;
    private final String text;

    StagedTextWrite(WritableConfigSource target, String text) {
        this.target = target;
        this.text = text;
    }

    @Override
    public ConfigSource source() {
        return ConfigSource.of(target.id(), text);
    }

    @Override
    public void commit() {
        target.write(text);
    }

    @Override
    public void discard() {
        // Nothing was written anywhere yet.
    }
}
