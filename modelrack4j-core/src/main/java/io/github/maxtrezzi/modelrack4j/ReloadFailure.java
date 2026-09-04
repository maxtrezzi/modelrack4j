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

import java.util.List;
import java.util.Objects;

/**
 * A reload that was rejected, leaving the previous snapshot live.
 *
 * <p>Delivered exactly once per rejected reload. Nothing was swapped: every bundle the
 * registry held before the attempt is still the bundle it hands out, so an application that
 * ignores this callback keeps running on the last configuration that was known good — which
 * is the point of rejecting the snapshot rather than applying part of it.
 *
 * @param sources the layers the registry was reading, lowest precedence first
 * @param cause why the snapshot was rejected — usually a {@link ConfigValidationException}
 *     naming the offending block, or a {@link ConfigAccessException} when a layer could not
 *     be read at all, but a provider builder may throw anything
 */
public record ReloadFailure(List<ConfigSource> sources, Exception cause) {

    /** @throws NullPointerException if any component is null */
    public ReloadFailure {
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        Objects.requireNonNull(cause, "cause");
    }
}
