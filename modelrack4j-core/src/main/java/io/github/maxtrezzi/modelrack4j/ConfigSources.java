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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Validation shared by the {@link ConfigSource} implementations and the builder. */
final class ConfigSources {

    private ConfigSources() {
    }

    /**
     * Checks that an identifier can actually identify something.
     *
     * @param id the candidate identifier
     * @return the identifier
     * @throws ConfigValidationException if it is null or blank
     */
    static String requireUsableId(String id) {
        if (id == null || id.isBlank()) {
            throw new ConfigValidationException(
                    "A configuration source needs a non-blank id; it labels the layer in"
                            + " error messages");
        }
        return id;
    }

    /**
     * Copies the sources, rejecting an empty list and duplicate identifiers.
     *
     * @param sources the layers, lowest precedence first
     * @return an immutable copy
     * @throws ConfigValidationException if the list is empty or two sources share an id
     */
    static List<ConfigSource> validated(List<ConfigSource> sources) {
        Objects.requireNonNull(sources, "sources");
        if (sources.isEmpty()) {
            throw new ConfigValidationException("At least one configuration source is required");
        }
        List<ConfigSource> copy = List.copyOf(sources);
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (ConfigSource source : copy) {
            // No null check on the source itself: List.copyOf above already rejects a null
            // element, so a check here could never fire.
            String id = requireUsableId(source.id());
            if (!seen.add(id)) {
                duplicates.add(id);
            }
        }
        if (!duplicates.isEmpty()) {
            // Ambiguous ids make every later error message useless, and usually mean the
            // same layer was passed twice.
            throw new ConfigValidationException(
                    "Configuration sources must have distinct ids; repeated: " + duplicates);
        }
        return copy;
    }
}
