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
import java.util.function.Supplier;

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
 * tokens, whether it takes a key or needs an address — are not here, because this type does
 * not know which provider it names; those run during registry build against the provider's
 * own factory.
 *
 * @param name the configuration name, as written in the config file, e.g. {@code SL}
 * @param description a short human-readable note on what this configuration is for, or
 *     empty when the file does not say
 * @param provider the provider id, matched against the factories on the classpath
 * @param apiKey the credential, or empty when the block sets none. Whether a block may or
 *     must set one depends on the provider (ADR-0062)
 * @param baseUrl the address of the server to call, or empty to use the provider's own
 *     default. Whether a block may or must set one depends on the provider (ADR-0062)
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
        Optional<String> apiKey,
        Optional<String> baseUrl,
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
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(temperature, "temperature");
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(customPropertiesText, "customPropertiesText");
        requireText(name, name, "name");
        requireText(name, provider, "provider");
        requireText(name, modelName, "model-name");
        Objects.requireNonNull(timeout, "timeout");

        requireNotBlankIfPresent(name, description, "description");
        requireNotBlankIfPresent(name, apiKey, "api-key");
        requireNotBlankIfPresent(name, baseUrl, "base-url");
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

    /**
     * Refuses an optional value that is present but blank.
     *
     * @implNote Present-but-blank is a mistake rather than a way to say "none": HOCON
     *     already has one, and {@code key = null} removes the key outright. For
     *     {@code api-key} a blank value would otherwise reach the provider as an empty
     *     credential, and for {@code base-url} as an address with no host.
     */
    private static void requireNotBlankIfPresent(
            String name, Optional<String> value, String key) {
        if (value.isPresent() && value.get().isBlank()) {
            throw new ConfigValidationException("llm." + name + "." + key
                    + " is present but blank. Remove the key, or set it to null"
                    + " to clear one set by a lower layer");
        }
    }

    private static void requireText(String name, String value, String key) {
        if (value == null || value.isBlank()) {
            throw new ConfigValidationException(
                    "llm." + name + "." + key + " is required and must not be blank");
        }
    }

    /**
     * Returns every component, with the three that may hold a credential redacted:
     * {@link #apiKey()} whenever it is present, the user-info part of {@link #baseUrl()}
     * whenever it has one, and {@link #customPropertiesText()} whenever it is not empty.
     *
     * @return a description safe to log
     * @implNote The generated {@code toString()} of a record prints every component, and
     *     {@link #apiKey()} holds the credential after substitution — the real key, not the
     *     {@code ${VAR}} the file was written with. This record is public and reachable as
     *     {@code registry.get(name).config()}, so one {@code log.info("{}", config)} in an
     *     application would put the key in a log file. The same reasoning already applies to
     *     {@link ConfigSource#id()}, which the library itself prints.
     *     <p>A {@code base-url} is printed, because which server a block calls is what a
     *     reader of a log line needs to know. Only its user-info is replaced: a URL of the form
     *     {@code https://user:secret@host} carries a credential in the address itself.
     *     <p>A custom property is redacted for the same reason and one step further: a
     *     substitution resolves inside that block too, so the library has to assume a property
     *     may be a credential. Not even the key names are printed, because a block with one
     *     key is identified by that key alone. {@code {}} is printed as itself, since an empty
     *     block hides nothing.
     *     <p>Only {@code toString()} changes. {@code equals} and {@code hashCode} stay as the
     *     record generates them, because the per-name reload diff is record equality on this
     *     type (ADR-0006) and it has to keep seeing a changed key as a changed configuration.
     */
    @Override
    public String toString() {
        return "LlmConfig[name=" + name
                + ", description=" + description
                + ", provider=" + provider
                + ", apiKey=" + (apiKey.isPresent() ? "***" : apiKey)
                + ", baseUrl=" + baseUrl.map(LlmConfig::withoutUserInfo)
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
     * Replaces the user-info part of a URL with {@code ***}.
     *
     * @param url the configured address, which may not be a valid URL
     * @return the same text with everything between the scheme and the last {@code @}
     *     replaced, or unchanged when there is no {@code @}
     * @implNote Done on the text rather than through {@link java.net.URI}, because the value
     *     has not been checked to be a URL and a parse failure here would make a
     *     configuration's {@code toString()} throw. The last {@code @} of the whole text is
     *     used rather than the last one inside the authority: a password written with an
     *     unencoded {@code /}, {@code ?} or {@code #} ends the authority early by the rules,
     *     and a parse by the rules then printed the rest of the password. The cost is that an
     *     {@code @} in a path or a query hides the host as well, which only makes a log line
     *     less informative. Hiding too much is the side this record already errs on — it hides
     *     the whole custom-properties block for the same reason (ADR-0047).
     */
    static String withoutUserInfo(String url) {
        int scheme = url.indexOf("://");
        int start = scheme < 0 ? 0 : scheme + 3;
        int at = url.lastIndexOf('@');
        return at < start ? url : url.substring(0, start) + "***" + url.substring(at);
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
            Optional<String> description = optionalString(reader, "description");
            String provider = reader.requiredString("provider");
            Optional<String> apiKey = optionalString(reader, "api-key");
            Optional<String> baseUrl = optionalString(reader, "base-url");
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

            return new LlmConfig(name, description, provider, apiKey, baseUrl, modelName,
                    temperature, timeout, logRequests, logResponses, streaming, memory,
                    moderation, customProperties);
        } catch (ConfigException e) {
            throw new ConfigValidationException(
                    "llm." + name + " is not a valid configuration block: " + e.getMessage(), e);
        }
    }

    private static Optional<String> optionalString(BlockReader reader, String key) {
        return reader.has(key) ? Optional.of(reader.string(key)) : Optional.empty();
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
            case "message-window" -> {
                requireNotSetForType(name, memory, type, "max-tokens",
                        "allow-remote-token-counting");
                int maxMessages = memory.requiredInt("max-messages");
                yield built(memory, () -> new MemoryConfig.MessageWindow(maxMessages));
            }
            case "token-window" -> {
                requireNotSetForType(name, memory, type, "max-messages");
                int maxTokens = memory.requiredInt("max-tokens");
                boolean remote = memory.has("allow-remote-token-counting")
                        && memory.bool("allow-remote-token-counting");
                yield built(memory, () -> new MemoryConfig.TokenWindow(maxTokens, remote));
            }
            default -> throw MemoryConfig.unknownType(type);
        };
    }

    /**
     * Builds the memory variant, unless a required value of it was missing.
     *
     * @param memory a reader over the {@code memory} sub-block
     * @param variant how to build it from the values read
     * @return the variant, or empty when a value it needs was not there
     * @implNote This is the one place in the parse that builds a validating object before the
     *     block has been checked for unknown keys, and building it from a placeholder is what
     *     made a misspelled {@code max-mesages} report {@code max-messages must be greater
     *     than 0} — the missing key rather than the misspelling that hid it, which is the
     *     failure ADR-0056 exists to prevent. The empty result never escapes: {@code
     *     rethrowFirstMissing()} throws for the same missing value a moment later, after
     *     {@code requireNoUnknownKeys} has had its say.
     */
    private static Optional<MemoryConfig> built(
            BlockReader memory, Supplier<MemoryConfig> variant) {
        return memory.anyMissing() ? Optional.empty() : Optional.of(variant.get());
    }

    /**
     * Refuses a memory key that belongs to the other variant.
     *
     * @param name the configuration name, for the message
     * @param memory a reader over the {@code memory} sub-block
     * @param type the configured memory type
     * @param inapplicable the keys the other variant uses
     * @throws ConfigValidationException if any of them is set
     * @implNote Asking for the key is what keeps it out of the closed-schema check, which
     *     would otherwise report it as a key this library does not know and tell the reader to
     *     check the spelling or move it to {@code custom-properties} — all three untrue of a
     *     key the reference lists (ADR-0056). It is still refused, because a block that sets
     *     {@code max-messages} beside {@code type = token-window} says two different things,
     *     and the one it does not mean is the one that would be ignored.
     */
    private static void requireNotSetForType(
            String name, BlockReader memory, String type, String... inapplicable) {
        for (String key : inapplicable) {
            if (memory.has(key)) {
                throw new ConfigValidationException("llm." + name + ".memory." + key
                        + " does not apply to memory.type = " + type + " ("
                        + memory.value(key).origin().description() + "). Remove it, or change"
                        + " the memory type.");
            }
        }
    }
}
