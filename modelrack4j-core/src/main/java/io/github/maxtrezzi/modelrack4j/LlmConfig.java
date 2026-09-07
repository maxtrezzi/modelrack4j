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
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import java.time.Duration;
import java.util.Map;
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
 * @param description a short human-readable note on what this configuration is for, or
 *     empty when the file does not say
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
 * @param customPropertiesText the block's {@code custom-properties} section rendered as JSON,
 *     or {@code "{}"} when it has none. The library carries it and never reads inside it
 */
public record LlmConfig(
        String name,
        Optional<String> description,
        String provider,
        String apiKey,
        String modelName,
        Optional<Double> temperature,
        Duration timeout,
        boolean logRequests,
        boolean logResponses,
        boolean streaming,
        Optional<MemoryConfig> memory,
        boolean moderationEnabled,
        String customPropertiesText) {

    /** The optional sub-block whose contents this library carries but never interprets. */
    static final String CUSTOM_PROPERTIES = "custom-properties";

    /** What {@link #customPropertiesText()} holds when the file has no such block. */
    static final String EMPTY_CUSTOM_PROPERTIES = "{}";

    /**
     * Validates every component.
     *
     * @throws ConfigValidationException if any value is missing, blank or out of range
     */
    public LlmConfig {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(temperature, "temperature");
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(customPropertiesText, "customPropertiesText");
        requireText(name, name, "name");
        requireText(name, provider, "provider");
        requireText(name, apiKey, "api-key");
        requireText(name, modelName, "model-name");
        Objects.requireNonNull(timeout, "timeout");

        // Present-but-blank is a mistake rather than a way to say "no description": HOCON
        // already has one, and `description = null` removes the key outright.
        if (description.isPresent() && description.get().isBlank()) {
            throw new ConfigValidationException("llm." + name
                    + ".description is present but blank. Remove the key, or set it to null"
                    + " to clear one set by a lower layer");
        }
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
     * Returns every component except the credential, which is replaced by {@code ***}.
     *
     * @return a description safe to log
     * @implNote The generated {@code toString()} of a record prints every component, and
     *     {@link #apiKey()} holds the credential after substitution — the real key, not the
     *     {@code ${VAR}} the file was written with. This record is public and reachable as
     *     {@code registry.get(name).config()}, so one {@code log.info("{}", config)} in an
     *     application would put the key in a log file. The same reasoning already applies to
     *     {@link ConfigSource#id()}, which the library itself prints.
     *     <p>Only {@code toString()} changes. {@code equals} and {@code hashCode} stay as the
     *     record generates them, because the per-name reload diff is record equality on this
     *     type (ADR-0006) and it has to keep seeing a changed key as a changed configuration.
     */
    @Override
    public String toString() {
        return "LlmConfig[name=" + name
                + ", description=" + description
                + ", provider=" + provider
                + ", apiKey=***"
                + ", modelName=" + modelName
                + ", temperature=" + temperature
                + ", timeout=" + timeout
                + ", logRequests=" + logRequests
                + ", logResponses=" + logResponses
                + ", streaming=" + streaming
                + ", memory=" + memory
                + ", moderationEnabled=" + moderationEnabled
                + ", customPropertiesText=" + (EMPTY_CUSTOM_PROPERTIES.equals(customPropertiesText)
                        ? EMPTY_CUSTOM_PROPERTIES
                        : "***")
                + ']';
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
        BlockReader reader = new BlockReader(block);
        try {
            Optional<String> description = reader.has("description")
                    ? Optional.of(reader.string("description"))
                    : Optional.<String>empty();
            String provider = reader.requiredString("provider");
            String apiKey = reader.requiredString("api-key");
            String modelName = reader.requiredString("model-name");
            Optional<Double> temperature = reader.has("temperature")
                    ? Optional.of(reader.dbl("temperature"))
                    : Optional.<Double>empty();
            Duration timeout = reader.duration("timeout");
            boolean logRequests = reader.bool("log-requests");
            boolean logResponses = reader.bool("log-responses");
            boolean streaming = reader.bool("streaming");
            Optional<MemoryConfig> memory = readMemory(name, reader);
            boolean moderation =
                    reader.has("moderation.enabled") && reader.bool("moderation.enabled");
            String customProperties = readCustomProperties(name, reader);

            // Before the record is built, so that a value the schema does not know is named
            // rather than reported as whatever the missing real key would have been. A block
            // that is invalid in both ways is told about the unknown key first, because that
            // is usually what caused the other (ADR-0056).
            reader.requireNoUnknownKeys(name);
            reader.rethrowFirstMissing();

            return new LlmConfig(name, description, provider, apiKey, modelName, temperature,
                    timeout, logRequests, logResponses, streaming, memory, moderation,
                    customProperties);
        } catch (ConfigException e) {
            throw new ConfigValidationException(
                    "llm." + name + " is not a valid configuration block: " + e.getMessage(), e);
        }
    }

    /**
     * Renders the {@code custom-properties} block as the text a handler receives.
     *
     * @param name the configuration name, for the message
     * @param block the merged block
     * @return the block as JSON, or {@code "{}"} when the file has none
     * @throws ConfigValidationException if it is present but is not a block
     * @implNote The library carries this as text and interprets none of it (ADR-0055).
     *     Rendering happens here, beside the parse, so that the recording of which paths the
     *     schema knows sees this one asked for like any other.
     */
    private static String readCustomProperties(String name, BlockReader reader) {
        if (!reader.has(CUSTOM_PROPERTIES)) {
            return EMPTY_CUSTOM_PROPERTIES;
        }
        ConfigValue value = reader.value(CUSTOM_PROPERTIES);
        if (!(value instanceof ConfigObject properties)) {
            throw new ConfigValidationException("llm." + name + "." + CUSTOM_PROPERTIES
                    + " must be a block of values, but is of type " + value.valueType()
                    + " (" + value.origin().description() + ")");
        }
        return withoutClearedKeys(properties).render(ConfigRenderOptions.concise());
    }

    /**
     * Drops the keys a higher layer cleared with {@code = null}, at every depth.
     *
     * @param object the merged sub-block
     * @return the same block with cleared keys gone
     * @implNote Rendering the merged object keeps them, as {@code "max-retries":null}, so
     *     without this a cleared key would reach the application as an explicit null rather
     *     than as absent — which is not what clearing means, and disagrees with
     *     {@code hasPath}, which already answers false for it. ADR-0032 established the idiom
     *     for a value inside a block, and this is that same block one level down.
     */
    private static ConfigObject withoutClearedKeys(ConfigObject object) {
        ConfigObject kept = object;
        for (Map.Entry<String, ConfigValue> entry : object.entrySet()) {
            ConfigValue value = entry.getValue();
            if (value.valueType() == ConfigValueType.NULL) {
                kept = kept.withoutKey(entry.getKey());
            } else if (value instanceof ConfigObject nested) {
                kept = kept.withValue(entry.getKey(), withoutClearedKeys(nested));
            }
        }
        return kept;
    }

    private static Optional<MemoryConfig> readMemory(String name, BlockReader block) {
        if (!block.has("memory")) {
            return Optional.empty();
        }
        BlockReader memory = block.scoped("memory");
        if (!memory.has("type")) {
            throw new ConfigValidationException(
                    "llm." + name + ".memory is present but memory.type is missing."
                            + " Supported values are message-window and token-window");
        }
        String type = memory.string("type");
        return switch (type) {
            case "message-window" -> Optional.of(
                    new MemoryConfig.MessageWindow(memory.requiredInt("max-messages")));
            case "token-window" -> Optional.of(new MemoryConfig.TokenWindow(
                    memory.requiredInt("max-tokens"),
                    memory.has("allow-remote-token-counting")
                            && memory.bool("allow-remote-token-counting")));
            default -> throw MemoryConfig.unknownType(type);
        };
    }
}
