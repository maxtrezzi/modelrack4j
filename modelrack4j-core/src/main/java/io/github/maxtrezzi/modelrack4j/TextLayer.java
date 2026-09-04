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
 * A layer that is text and nothing else: a database row, a value from a configuration
 * service, text built in memory. Nothing the library ships can watch it.
 *
 * @param source the source this layer was built from
 */
record TextLayer(ConfigSource source) implements Layer {

    /**
     * {@inheritDoc}
     *
     * @implNote A layer with no directory of its own gets Typesafe Config's documented
     *     behaviour for text, which looks an {@code include} up on the classpath.
     */
    @Override
    public Config parse(ConfigParseOptions options) {
        String text = source.text();
        if (text == null) {
            throw new ConfigValidationException(
                    "Configuration source " + source.id() + " returned no text");
        }
        return ConfigFactory.parseString(text, options);
    }

    @Override
    public Optional<Path> watchTarget() {
        return Optional.empty();
    }
}
