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
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * One named configuration block, parsed and validated.
 *
 * <p>This is an immutable record, and that is load-bearing rather than stylistic: reload
 * detects per-name changes by record equality, so two blocks that parse to equal values are
 * the same configuration and their bundle is carried over rather than rebuilt. Every
 * component therefore has value equality.
 *
 * <p>Validation happens in the compact constructor, so an instance that exists is valid.
 * Capability checks that depend on the provider — whether it can moderate, how it counts
 * tokens — are not here, because this type does not know which provider it names; those run
 * during registry build against the provider's own factory.
 *
 * @param name the configuration name, as written in the config file, e.g. {@code SL}
 * @param provider the provider id, matched against the factories on the classpath
 * @param apiKey the credential, never blank
 * @param modelName the provider's model identifier
 * @param temperature sampling temperature, or empty to accept the provider's default
 * @param timeout request timeout, always positive
 * @param logRequests whether the provider should log requests
 * @param logResponses whether the provider should log responses
 * @param streaming whether a {@code StreamingChatModel} is built alongside the chat model
 * @param memory how conversation memory is bounded, or empty for no memory provider
 * @param moderationEnabled whether a {@code ModerationModel} is built
 */
public record LlmConfig(
        String name,
        String provider,
        String apiKey,
        String modelName,
        Optional<Double> temperature,
        Duration timeout,
        boolean logRequests,
        boolean logResponses,
        boolean streaming,
        Optional<MemoryConfig> memory,
        boolean moderationEnabled) {

    /**
     * Validates every component.
     *
     * @throws ConfigValidationException if any value is missing, blank or out of range
     */
    public LlmConfig {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(temperature, "temperature");
        Objects.requireNonNull(memory, "memory");
        requireText(name, name, "name");
        requireText(name, provider, "provider");
        requireText(name, apiKey, "api-key");
        requireText(name, modelName, "model-name");
        Objects.requireNonNull(timeout, "timeout");

        if (timeout.isZero() || timeout.isNegative()) {
            throw new ConfigValidationException(
                    "llm." + name + ".timeout must be positive, was " + timeout);
        }
        // Providers disagree on the upper bound, so this only rejects values no provider
        // accepts rather than pretending to know each provider's ceiling.
        if (temperature.isPresent() && (temperature.get() < 0.0 || temperature.get() > 2.0)) {
            throw new ConfigValidationException("llm." + name
                    + ".temperature must be between 0.0 and 2.0, was " + temperature.get());
        }
    }

    private static void requireText(String name, String value, String key) {
        if (value == null || value.isBlank()) {
            throw new ConfigValidationException(
                    "llm." + name + "." + key + " is required and must not be blank");
        }
    }

    /**
     * Parses one named block. The block is expected to already carry the library's defaults
     * as a fallback layer, so every optional key is present by the time this reads it.
     *
     * @param name the configuration name
     * @param block the merged, resolved config for that name
     * @return the validated configuration
     * @throws ConfigValidationException if the block is malformed or a value is invalid
     */
    public static LlmConfig fromBlock(String name, Config block) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(block, "block");
        try {
            return new LlmConfig(
                    name,
                    block.getString("provider"),
                    block.getString("api-key"),
                    block.getString("model-name"),
                    block.hasPath("temperature")
                            ? Optional.of(block.getDouble("temperature"))
                            : Optional.empty(),
                    block.getDuration("timeout"),
                    block.getBoolean("log-requests"),
                    block.getBoolean("log-responses"),
                    block.getBoolean("streaming"),
                    readMemory(name, block),
                    block.hasPath("moderation.enabled") && block.getBoolean("moderation.enabled"));
        } catch (ConfigException e) {
            throw new ConfigValidationException(
                    "llm." + name + " is not a valid configuration block: " + e.getMessage(), e);
        }
    }

    private static Optional<MemoryConfig> readMemory(String name, Config block) {
        if (!block.hasPath("memory")) {
            return Optional.empty();
        }
        Config memory = block.getConfig("memory");
        if (!memory.hasPath("type")) {
            throw new ConfigValidationException(
                    "llm." + name + ".memory is present but memory.type is missing."
                            + " Supported values are message-window and token-window");
        }
        String type = memory.getString("type");
        switch (type) {
            case "message-window":
                return Optional.of(new MemoryConfig.MessageWindow(memory.getInt("max-messages")));
            case "token-window":
                return Optional.of(new MemoryConfig.TokenWindow(
                        memory.getInt("max-tokens"),
                        memory.hasPath("allow-remote-token-counting")
                                && memory.getBoolean("allow-remote-token-counting")));
            default:
                throw MemoryConfig.unknownType(type);
        }
    }
}
