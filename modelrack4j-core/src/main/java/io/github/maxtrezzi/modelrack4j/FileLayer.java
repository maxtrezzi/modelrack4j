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
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A layer held in a file: it is parsed through the file, and it can be watched.
 *
 * @param source the source this layer was built from
 * @param file the file it reads, as configured
 */
record FileLayer(ConfigSource source, Path file) implements Layer {

    /**
     * {@inheritDoc}
     *
     * @implNote Parsed through the file rather than through its text, because
     *     {@code include "sibling.conf"} resolves relative to the file that contains the
     *     line and only {@code parseFile} knows which file that is. Handing the same bytes
     *     to {@code parseString} moves the includer to the classpath, where an
     *     allow-missing include quietly finds nothing (ADR-0042).
     *     <p>{@code setAllowMissing(false)} so a layer that disappeared is an error rather
     *     than an empty layer.
     */
    @Override
    public Config parse(ConfigParseOptions options) {
        return ConfigFactory.parseFile(file.toFile(), options.setAllowMissing(false));
    }

    /**
     * {@inheritDoc}
     *
     * @implNote The configured path, never its resolved target: the watcher registers on the
     *     directory holding a symbolic link rather than the directory holding its target, so
     *     that a swapped link is seen (ADR-0024).
     */
    @Override
    public Optional<Path> watchTarget() {
        return Optional.of(file);
    }
}
