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
 * The proposed text of a store, staged in a real file beside the layer it will replace, so
 * it can be validated the way the committed file will actually be read.
 *
 * @param id the <em>target's</em> id, not the staged file's, so an error message names the
 *     layer the application knows about rather than a temporary path it never chose
 * @param file the staged file
 */
record StagedFileSource(String id, Path file) implements FileBacked {

    StagedFileSource {
        Objects.requireNonNull(file, "file");
        ConfigSources.requireUsableId(id);
    }

    @Override
    public String text() {
        return FileConfigSource.read(file);
    }
}
