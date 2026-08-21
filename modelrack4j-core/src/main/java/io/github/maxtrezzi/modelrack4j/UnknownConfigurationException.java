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
 * Thrown when a configuration name is requested from the registry and no bundle is bound to
 * it — either it was never configured, or it was removed by a reload.
 *
 * <p>A name that disappears from the configuration is removed from the registry rather than
 * retained, so a lookup that succeeded before a reload may throw afterwards. Callers holding
 * a name across reloads must be prepared for this.
 *
 * @implNote Superseded bundles are not closed, so an object obtained before the removal stays
 *     usable for in-flight work; only the lookup fails.
 */
public final class UnknownConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String configurationName;

    /**
     * Creates an exception naming the configuration that could not be resolved.
     *
     * @param configurationName the requested name, never {@code null}
     */
    public UnknownConfigurationException(String configurationName) {
        super("No configuration named '"
                + Objects.requireNonNull(configurationName, "configurationName")
                + "' is present in the registry");
        this.configurationName = configurationName;
    }

    /**
     * Returns the configuration name that was requested.
     *
     * @return the requested name, never {@code null}
     */
    public String configurationName() {
        return configurationName;
    }
}
