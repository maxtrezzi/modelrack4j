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
 * A staged write to a file layer: written beside the target, then moved onto it.
 *
 * @implNote A top-level class rather than one nested in {@link StagedWrite}, because a class
 *     declared inside an interface is implicitly {@code public} and would have shown up in
 *     the jar's public surface for no reason.
 */
final class StagedFileWrite implements StagedWrite {

    private final WritableFileConfigSource target;
    private final Path staged;

    StagedFileWrite(WritableFileConfigSource target, String text) {
        this.target = target;
        this.staged = target.stage(text);
    }

    @Override
    public ConfigSource source() {
        return new StagedFileSource(target.id(), staged);
    }

    @Override
    public void commit() {
        target.commitStaged(staged);
    }

    @Override
    public void discard() {
        target.discardStaged(staged);
    }
}
