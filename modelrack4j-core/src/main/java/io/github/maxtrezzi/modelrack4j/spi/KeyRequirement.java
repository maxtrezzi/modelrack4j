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
package io.github.maxtrezzi.modelrack4j.spi;

/**
 * Whether a configuration block may set a key, and whether it must.
 *
 * <p>One type serves both keys a provider has an opinion on, {@code api-key} and
 * {@code base-url}, which is why the names do not mention either. It has three values rather
 * than two booleans because one combination of two booleans — required but not permitted —
 * is meaningless, and with this type it cannot be written (ADR-0062).
 *
 * @see ProviderFactory#apiKeyRequirement()
 * @see ProviderFactory#baseUrlRequirement()
 */
public enum KeyRequirement {

    /** The provider does not use this key, so a block that sets it is refused. */
    FORBIDDEN,

    /** A block may set the key or leave it out. */
    OPTIONAL,

    /** A block that leaves the key out is refused. */
    MANDATORY;

    /**
     * Returns whether a block may set the key.
     *
     * @return {@code true} unless this is {@link #FORBIDDEN}
     */
    public boolean permitted() {
        return this != FORBIDDEN;
    }

    /**
     * Returns whether a block must set the key.
     *
     * @return {@code true} only for {@link #MANDATORY}
     */
    public boolean required() {
        return this == MANDATORY;
    }
}
