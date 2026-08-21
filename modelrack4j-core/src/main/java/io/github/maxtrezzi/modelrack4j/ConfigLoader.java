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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns a list of config files into one merged, resolved {@link Config}.
 *
 * <p>The ordering contract and the single resolve are the whole point of this class, and both
 * are easy to get wrong in ways that only fail in layered setups.
 */
final class ConfigLoader {

    /** Path under which the library's documented defaults live in {@code reference.conf}. */
    private static final String DEFAULTS_PATH = "modelrack4j.defaults";

    private ConfigLoader() {
    }

    /**
     * Parses every layer, merges them, and resolves the result exactly once.
     *
     * @param files the layers, lowest precedence first
     * @return the merged and resolved configuration
     * @throws ConfigValidationException if a file is missing or cannot be parsed, or a
     *     mandatory substitution is unresolved after merging
     */
    static Config load(List<Path> files) {
        Objects.requireNonNull(files, "files");
        if (files.isEmpty()) {
            throw new ConfigValidationException("At least one configuration file is required");
        }

        List<Config> layers = new ArrayList<>(files.size());
        for (Path file : files) {
            Objects.requireNonNull(file, "configuration file path");
            if (!Files.isReadable(file)) {
                throw new ConfigValidationException(
                        "Configuration file does not exist or is not readable: " + file);
            }
            // parseFile ONLY. Resolving here would break mandatory substitution: a ${VAR} in
            // a lower layer would fail even when a higher layer replaces that whole key, and
            // cross-layer references would not see the merged values.
            layers.add(ConfigFactory.parseFile(
                    file.toFile(), ConfigParseOptions.defaults().setAllowMissing(false)));
        }

        // Highest precedence first, each falling back to the one below it.
        Config merged = layers.get(layers.size() - 1);
        for (int i = layers.size() - 2; i >= 0; i--) {
            merged = merged.withFallback(layers.get(i));
        }
        merged = merged.withFallback(referenceConfig());

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
