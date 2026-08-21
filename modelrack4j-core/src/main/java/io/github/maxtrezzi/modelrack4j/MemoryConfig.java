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
 * How conversation memory is bounded. Exactly two variants exist, and they take different
 * parameters, so the type is sealed rather than a single record with unused fields — an
 * invalid combination cannot be constructed.
 */
public sealed interface MemoryConfig {

    /**
     * Bounds memory by message count. Counting messages needs no provider capability, so
     * this variant works with every provider.
     *
     * @param maxMessages how many messages to retain, greater than zero
     */
    record MessageWindow(int maxMessages) implements MemoryConfig {

        /** @throws ConfigValidationException if {@code maxMessages} is not positive */
        public MessageWindow {
            if (maxMessages <= 0) {
                throw new ConfigValidationException(
                        "memory.max-messages must be greater than 0, was " + maxMessages);
            }
        }
    }

    /**
     * Bounds memory by token count, which requires the provider to supply a
     * {@code TokenCountEstimator}.
     *
     * @param maxTokens how many tokens to retain, greater than zero
     * @param allowRemoteTokenCounting whether the configuration accepts a billed network
     *     call per eviction, required when the provider counts remotely
     */
    record TokenWindow(int maxTokens, boolean allowRemoteTokenCounting) implements MemoryConfig {

        /** @throws ConfigValidationException if {@code maxTokens} is not positive */
        public TokenWindow {
            if (maxTokens <= 0) {
                throw new ConfigValidationException(
                        "memory.max-tokens must be greater than 0, was " + maxTokens);
            }
        }
    }

    /**
     * Returns the discriminator value that selects this variant in configuration.
     *
     * @return {@code "message-window"} or {@code "token-window"}
     */
    default String typeName() {
        if (this instanceof MessageWindow) {
            return "message-window";
        }
        if (this instanceof TokenWindow) {
            return "token-window";
        }
        // The ternary this replaced would have labelled a new variant "token-window",
        // which is worse than failing: it would be quietly wrong in error messages.
        throw new IllegalStateException("Unhandled memory variant: " + getClass().getName());
    }

    /**
     * Builds the failure for a {@code memory.type} value that matches no variant. Returned
     * rather than thrown so the caller can {@code throw} it, which keeps the compiler aware
     * that the branch does not fall through.
     *
     * @param typeName the configured discriminator value
     * @return the exception to throw
     */
    static ConfigValidationException unknownType(String typeName) {
        return new ConfigValidationException("Unknown memory.type '"
                + Objects.requireNonNull(typeName, "typeName")
                + "'. Supported values are message-window and token-window");
    }
}
