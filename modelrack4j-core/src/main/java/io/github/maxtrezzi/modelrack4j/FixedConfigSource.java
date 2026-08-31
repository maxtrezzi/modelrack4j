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

import java.util.Objects;

/**
 * A source over text that was captured once. Backs {@link ConfigSource#of(String, String)}.
 */
record FixedConfigSource(String id, String text) implements ConfigSource {

    FixedConfigSource {
        Objects.requireNonNull(text, "text");
        ConfigSources.requireUsableId(id);
    }
}
