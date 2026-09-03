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
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns the configured layers into one complete snapshot of bundles.
 *
 * <p>A snapshot is the whole map of name to bundle, which is the unit the registry publishes
 * ({@link ReloadChange}, ADR-0012). Loading is all-or-nothing: this class either returns a
 * complete map or throws, and never leaves a half-built one behind for the caller to
 * reconcile. That is the staging area the atomicity rule needs — builders throw for reasons
 * {@code validate()} cannot predict, so the last bundle has to be built successfully before
 * the first one is published.
 *
 * <p>One instance serves the life of a registry: the provider factories are discovered once,
 * because the classpath cannot change between reloads and a duplicate provider should fail
 * at startup rather than at the first edit.
 */
final class SnapshotLoader {

    /** Root path holding the named blocks. */
    static final String ROOT_PATH = "llm";

    private final List<ConfigSource> sources;
    private final Map<String, ProviderFactory> factories;

    SnapshotLoader(List<ConfigSource> sources) {
        this.sources = ConfigSources.validated(sources);
        this.factories = discoverFactories();
    }

    /**
     * Parses every layer and returns the snapshot it describes.
     *
     * @param previous the live snapshot, whose bundles are carried over where the parsed
     *     configuration is unchanged; empty on the first load
     * @return a complete snapshot, sorted by name
     * @throws ConfigValidationException if any layer is unreadable, any block is invalid, or
     *     any provider rejects or fails to build its configuration
     */
    Map<String, LlmBundle> load(Map<String, LlmBundle> previous) {
        return load(previous, sources);
    }

    /**
     * Parses the given layers and returns the snapshot they describe.
     *
     * @param previous the live snapshot, whose bundles are carried over where the parsed
     *     configuration is unchanged
     * @param layers the layers to read, which during a store is this registry's list with
     *     the layer being written replaced by its staged text
     * @return a complete snapshot, sorted by name
     * @throws ConfigValidationException if any layer is unreadable, any block is invalid, or
     *     any provider rejects or fails to build its configuration
     */
    Map<String, LlmBundle> load(Map<String, LlmBundle> previous, List<ConfigSource> layers) {
        Config resolved = ConfigLoader.load(layers);
        if (!resolved.hasPath(ROOT_PATH)) {
            throw new ConfigValidationException(
                    "No '" + ROOT_PATH + "' block found in any configuration layer");
        }

        Config defaults = ConfigLoader.defaults();
        ConfigObject root = resolved.getObject(ROOT_PATH);

        Map<String, LlmBundle> built = new TreeMap<>();
        // Sorted so that when several blocks are invalid, which one is reported is stable
        // between runs instead of following map iteration order.
        for (String name : new TreeSet<>(root.keySet())) {
            ConfigValue value = root.get(name);
            if (!(value instanceof ConfigObject block)) {
                throw new ConfigValidationException("llm." + name
                        + " must be a configuration block, but is of type "
                        + value.valueType() + " (" + value.origin().description() + ")");
            }
            LlmConfig config =
                    LlmConfig.fromBlock(name, block.toConfig().withFallback(defaults));

            // ADR-0006: the per-name diff is record equality on the parsed config. An
            // unchanged block keeps its existing instance, so a reload rebuilds only what
            // the user actually edited.
            LlmBundle carried = previous.get(name);
            built.put(name, carried != null && carried.config().equals(config)
                    ? carried
                    : buildBundle(config));
        }

        if (built.isEmpty()) {
            throw new ConfigValidationException(
                    "The '" + ROOT_PATH + "' block is empty: no configurations to build");
        }
        return Collections.unmodifiableMap(built);
    }

    private LlmBundle buildBundle(LlmConfig config) {
        ProviderFactory factory = factories.get(config.provider());
        if (factory == null) {
            List<String> available = new ArrayList<>(factories.keySet());
            Collections.sort(available);
            throw new ConfigValidationException(path(config)
                    + ".provider is '" + config.provider() + "', for which no provider"
                    + " module is on the classpath. Available providers: "
                    + (available.isEmpty() ? "(none)" : String.join(", ", available)));
        }

        validateCapabilities(config, factory);
        factory.validate(config);

        ChatModel chatModel = factory.createChatModel(config);
        if (chatModel == null) {
            throw new ConfigValidationException(path(config) + ": provider '"
                    + config.provider() + "' produced no chat model, which every bundle"
                    + " must have.");
        }

        return new LlmBundle(
                config,
                chatModel,
                config.streaming()
                        ? requireProduced(factory.createStreamingChatModel(config), config,
                                "streaming = true", "streaming chat model")
                        : Optional.empty(),
                config.moderationEnabled()
                        ? requireProduced(factory.createModerationModel(config), config,
                                "moderation.enabled = true", "moderation model")
                        : Optional.empty(),
                buildMemoryProvider(config, factory));
    }

    /** Anchors a message to the block the user wrote, e.g. {@code llm.SL}. */
    private static String path(LlmConfig config) {
        return ROOT_PATH + "." + config.name();
    }

    /**
     * Fails when the configuration asked for a capability and the factory produced nothing,
     * rather than handing back a bundle quietly missing what was requested. Silently
     * dropping it would defeat the fail-fast contract: the configuration would look honoured
     * and the object would not be there.
     */
    private static <T> Optional<T> requireProduced(
            Optional<T> produced, LlmConfig config, String requestedBy, String what) {
        if (produced == null || produced.isEmpty()) {
            throw new ConfigValidationException(path(config) + " sets "
                    + requestedBy + ", but provider '" + config.provider()
                    + "' produced no " + what + ".");
        }
        return produced;
    }

    /**
     * Applies the capability rules that depend only on what the factory reports, so no
     * provider module has to restate them.
     */
    private static void validateCapabilities(LlmConfig config, ProviderFactory factory) {
        if (config.moderationEnabled() && !factory.supportsModeration()) {
            throw new ConfigValidationException(path(config)
                    + " sets moderation.enabled = true, but provider '" + config.provider()
                    + "' ships no moderation model. Remove the moderation block, or route"
                    + " moderation through an OpenAI-family configuration.");
        }

        Optional<MemoryConfig> configured = config.memory();
        if (configured.isEmpty()
                || !(configured.get() instanceof MemoryConfig.TokenWindow window)) {
            // Only token-window memory depends on a provider capability.
            return;
        }
        TokenEstimation estimation = factory.tokenEstimation();
        if (estimation == null) {
            throw new ConfigValidationException(path(config) + ": provider '"
                    + config.provider() + "' reported no token estimation capability, so"
                    + " whether token-window memory is affordable cannot be decided.");
        }

        if (estimation == TokenEstimation.ABSENT) {
            throw new ConfigValidationException(path(config)
                    + " uses memory.type = token-window, but provider '" + config.provider()
                    + "' ships no token count estimator, so token-window memory cannot be"
                    + " built. Use memory.type = message-window instead.");
        }
        // The message names the flag on purpose: a validation error that hides its own
        // escape hatch turns opt-in into outright rejection.
        if (estimation == TokenEstimation.REMOTE && !window.allowRemoteTokenCounting()) {
            throw new ConfigValidationException(path(config)
                    + " uses memory.type = token-window, but provider '" + config.provider()
                    + "' counts tokens by calling its API, so every memory eviction makes a"
                    + " billed, rate-limited network request. Set"
                    + " memory.allow-remote-token-counting = true to accept that cost.");
        }
    }

    private static Optional<ChatMemoryProvider> buildMemoryProvider(
            LlmConfig config, ProviderFactory factory) {
        if (config.memory().isEmpty()) {
            return Optional.empty();
        }
        MemoryConfig memory = config.memory().get();
        if (memory instanceof MemoryConfig.MessageWindow window) {
            int maxMessages = window.maxMessages();
            return Optional.of(memoryId -> MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(maxMessages)
                    .build());
        }
        if (memory instanceof MemoryConfig.TokenWindow window) {
            int maxTokens = window.maxTokens();
            TokenCountEstimator estimator = factory.createTokenCountEstimator(config)
                    .orElseThrow(() -> new ConfigValidationException(path(config)
                            + " uses memory.type = token-window, but provider '"
                            + config.provider() + "' supplied no token count estimator"));
            return Optional.of(memoryId -> TokenWindowChatMemory.builder()
                    .id(memoryId)
                    .maxTokens(maxTokens, estimator)
                    .build());
        }
        // MemoryConfig is sealed, but Java 17 has no pattern switch, so the compiler does
        // not check this chain for exhaustiveness. A new variant must fail loudly here
        // rather than fall through to a ClassCastException.
        throw new IllegalStateException(
                "Unhandled memory variant: " + memory.getClass().getName());
    }

    private static Map<String, ProviderFactory> discoverFactories() {
        Map<String, ProviderFactory> byId = new LinkedHashMap<>();
        for (ProviderFactory factory : ServiceLoader.load(ProviderFactory.class)) {
            String id = factory.providerId();
            if (id == null || id.isBlank()) {
                throw new ConfigValidationException(
                        "Provider factory " + factory.getClass().getName()
                                + " returned a blank providerId");
            }
            ProviderFactory previous = byId.putIfAbsent(id, factory);
            if (previous != null) {
                throw new ConfigValidationException("Two provider factories both claim"
                        + " providerId '" + id + "': " + previous.getClass().getName()
                        + " and " + factory.getClass().getName()
                        + ". Remove one of the provider modules from the classpath.");
            }
        }
        return byId;
    }
}
