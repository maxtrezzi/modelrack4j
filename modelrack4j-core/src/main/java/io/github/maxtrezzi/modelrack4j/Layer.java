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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigParseOptions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One configuration layer, as the registry's internals see it: a {@link ConfigSource} plus
 * the answers to the two questions the library asks about a layer — how to parse it, and
 * what to watch for it.
 *
 * <p>A {@code ConfigSource} deliberately names no file (ADR-0042), so those answers are not
 * on it. They used to be recovered with {@code instanceof FileBacked}, once in the loader and
 * once in the builder; this type asks that question once, in {@link #of(ConfigSource)}, and
 * every use site calls a method instead (ADR-0051).
 *
 * @implNote Package-private and sealed, so the set of layer kinds is closed and internal. It
 *     is not the place to add a public capability: making a layer's file part of the API is
 *     what ADR-0042 refused, twice.
 */
sealed interface Layer permits FileLayer, TextLayer {

    /** @return the source this layer was built from, which is what public types report */
    ConfigSource source();

    /**
     * Parses this layer, unresolved.
     *
     * @param options the parse options, already carrying the source's id as its origin
     * @return the parsed layer, not yet merged and not yet resolved
     * @throws ConfigAccessException if the layer cannot be reached
     * @throws com.typesafe.config.ConfigException if it cannot be parsed
     */
    Config parse(ConfigParseOptions options);

    /**
     * @return the file to watch for changes to this layer, empty when nothing can watch it
     */
    Optional<Path> watchTarget();

    /**
     * Recognises what kind of layer a source is. <strong>The only place that asks.</strong>
     *
     * @param source the layer's source
     * @return the layer
     */
    static Layer of(ConfigSource source) {
        Objects.requireNonNull(source, "configuration source");
        if (source instanceof FileBacked fileSource) {
            return new FileLayer(source, fileSource.file());
        }
        return new TextLayer(source);
    }

    /**
     * Recognises a whole list, keeping its order.
     *
     * @param sources the layers' sources, lowest precedence first
     * @return the layers, in the same order
     */
    static List<Layer> of(List<ConfigSource> sources) {
        List<Layer> layers = new ArrayList<>(sources.size());
        for (ConfigSource source : sources) {
            layers.add(of(source));
        }
        return List.copyOf(layers);
    }

    /**
     * Unwraps a list back to what public types carry.
     *
     * @param layers the layers
     * @return their sources, in the same order
     */
    static List<ConfigSource> sourcesOf(List<Layer> layers) {
        List<ConfigSource> sources = new ArrayList<>(layers.size());
        for (Layer layer : layers) {
            sources.add(layer.source());
        }
        return List.copyOf(sources);
    }
}
