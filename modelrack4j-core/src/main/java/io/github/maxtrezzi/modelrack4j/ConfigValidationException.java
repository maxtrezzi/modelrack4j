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

/**
 * Thrown when configuration is syntactically valid but semantically wrong — a missing
 * mandatory value, a value out of range, an unknown provider, or a capability the chosen
 * provider does not have.
 *
 * <p>Validation is fail-fast: this is thrown while the registry is being built, before any
 * model object exists, so an invalid configuration never becomes a live bundle.
 */
public final class ConfigValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception describing what is wrong with the configuration.
     *
     * @param message what failed validation, and where
     */
    public ConfigValidationException(String message) {
        super(message);
    }

    /**
     * Creates an exception describing what is wrong with the configuration, preserving the
     * underlying failure.
     *
     * @param message what failed validation, and where
     * @param cause the underlying failure, typically from the config parser
     */
    public ConfigValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
