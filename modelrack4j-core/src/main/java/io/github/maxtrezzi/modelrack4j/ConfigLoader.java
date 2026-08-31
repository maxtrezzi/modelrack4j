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
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns a list of configuration sources into one merged, resolved {@link Config}.
 *
 * <p>The ordering contract and the single resolve are the whole point of this class, and both
 * are easy to get wrong in ways that only fail in layered setups.
 */
final class ConfigLoader {

    /** Path holding the documented defaults inside {@code modelrack4j-reference.conf}. */
    private static final String DEFAULTS_PATH = "modelrack4j.defaults";

    private ConfigLoader() {
    }

    /**
     * Parses every layer, merges them, and resolves the result exactly once.
     *
     * @param sources the layers, lowest precedence first
     * @return the merged and resolved configuration
     * @throws ConfigValidationException if a source cannot produce its text or cannot be
     *     parsed, or a mandatory substitution is unresolved after merging
     */
    static Config load(List<ConfigSource> sources) {
        Objects.requireNonNull(sources, "sources");
        if (sources.isEmpty()) {
            throw new ConfigValidationException("At least one configuration source is required");
        }

        List<Config> layers = new ArrayList<>(sources.size());
        for (ConfigSource source : sources) {
            Objects.requireNonNull(source, "configuration source");
            // Parse ONLY. Resolving here would break mandatory substitution: a ${VAR} in a
            // lower layer would fail even when a higher layer replaces that whole key, and
            // cross-layer references would not see the merged values.
            //
            // The origin description is what puts the source's id into a parse error, so a
            // malformed database row reports "llm_config#42: 7: ..." rather than losing the
            // provenance a file used to get for free.
            try {
                layers.add(ConfigFactory.parseString(
                        text(source),
                        ConfigParseOptions.defaults().setOriginDescription(source.id())));
            } catch (ConfigException e) {
                // Wrapped so a malformed layer arrives as this library's own failure, which
                // is what LlmRegistry.build() and reload() document. The message already
                // names the source and the line, because of the origin description above.
                throw new ConfigValidationException(
                        "Configuration source " + source.id() + " could not be parsed: "
                                + e.getMessage(), e);
            }
        }

        // Highest precedence first, each falling back to the one below it.
        Config merged = layers.get(layers.size() - 1);
        for (int i = layers.size() - 2; i >= 0; i--) {
            merged = merged.withFallback(layers.get(i));
        }

        try {
            // Exactly once, on the merged result. Default resolve options fall back to
            // environment variables, so ${API_KEY} reads the environment and fails loudly
            // when unset, which is what mandatory substitution is for.
            return merged.resolve();
        } catch (ConfigException.UnresolvedSubstitution e) {
            throw new ConfigValidationException(
                    "A mandatory substitution is unresolved after merging all layers."
                            + " Set the environment variable, or override the value in a"
                            + " higher-precedence layer: " + e.getMessage(), e);
        } catch (ConfigException e) {
            throw new ConfigValidationException(
                    "Configuration could not be resolved: " + e.getMessage(), e);
        }
    }

    private static String text(ConfigSource source) {
        String text = source.text();
        if (text == null) {
            throw new ConfigValidationException(
                    "Configuration source " + source.id() + " returned no text");
        }
        return text;
    }

    /**
     * Returns the library's own defaults, which sit below every user layer.
     *
     * @return the defaults block from {@code reference.conf}
     */
    static Config defaults() {
        return referenceConfig().getConfig(DEFAULTS_PATH);
    }

    private static Config referenceConfig() {
        return ConfigFactory.parseResources(
                ConfigLoader.class.getClassLoader(), "modelrack4j-reference.conf");
    }
}
