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
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;

/**
 * Holds one ready-to-use {@link LlmBundle} per configured name.
 *
 * <p>Build it once at startup and ask it for a bundle whenever you need one:
 *
 * <pre>{@code
 * LlmRegistry registry = LlmRegistry.builder()
 *         .configFiles(List.of(defaults, product, customer))   // lowest -> highest
 *         .build();
 *
 * ChatModel model = registry.get("SL").chatModel();
 * }</pre>
 *
 * <p><strong>Ask the registry every time; do not cache the bundle.</strong> The registry is
 * the holder, and {@link #get(String)} always returns the current bundle for a name. Code
 * that fetches a bundle at startup and keeps it will keep working and will never see a
 * configuration change — the single most common mistake with reloadable configuration.
 *
 * <p>Building is fail-fast and all-or-nothing: every named block is parsed, validated and
 * built before the registry exists, so a registry that was returned has no broken bundles in
 * it.
 *
 * @implNote This class does not watch for file changes. Reload arrives in a later release;
 *     until then a registry holds the configuration as it was when built.
 */
public final class LlmRegistry implements AutoCloseable {

    /** Root path holding the named blocks. */
    private static final String ROOT_PATH = "llm";

    private final Map<String, LlmBundle> bundles;

    private LlmRegistry(Map<String, LlmBundle> bundles) {
        this.bundles = bundles;
    }

    /**
     * Returns a builder for a registry.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the current bundle for a name.
     *
     * @param name the configuration name, as written in the config file
     * @return the bundle bound to that name
     * @throws UnknownConfigurationException if no bundle is bound to the name
     */
    public LlmBundle get(String name) {
        LlmBundle bundle = bundles.get(Objects.requireNonNull(name, "name"));
        if (bundle == null) {
            throw new UnknownConfigurationException(name);
        }
        return bundle;
    }

    /**
     * Returns every configured name, in sorted order.
     *
     * @return an unmodifiable set of the names currently held
     */
    public Set<String> names() {
        return Collections.unmodifiableSet(bundles.keySet());
    }

    /**
     * Releases resources held by the registry itself.
     *
     * @implNote Bundles are deliberately not closed. In-flight requests may still hold one,
     *     and LangChain4j model objects are immutable and complete normally.
     */
    @Override
    public void close() {
        // Nothing to release yet: there is no watcher thread until reload lands.
    }

    /** Collects the inputs for a registry and builds it. */
    public static final class Builder {

        private List<Path> configFiles = List.of();

        private Builder() {
        }

        /**
         * Sets the configuration layers.
         *
         * @param files the layers, <strong>lowest precedence first</strong>, so the last
         *     entry wins on conflict
         * @return this builder
         */
        public Builder configFiles(List<Path> files) {
            this.configFiles = List.copyOf(Objects.requireNonNull(files, "files"));
            return this;
        }

        /**
         * Parses, validates and builds every configured bundle.
         *
         * @return the registry
         * @throws ConfigValidationException if any layer is unreadable, any block is invalid,
         *     or any provider rejects its configuration
         */
        public LlmRegistry build() {
            Config resolved = ConfigLoader.load(configFiles);
            if (!resolved.hasPath(ROOT_PATH)) {
                throw new ConfigValidationException(
                        "No '" + ROOT_PATH + "' block found in any configuration layer");
            }

            Map<String, ProviderFactory> factories = discoverFactories();
            Config defaults = ConfigLoader.defaults();
            ConfigObject root = resolved.getObject(ROOT_PATH);

            Map<String, LlmBundle> built = new TreeMap<>();
            for (String name : new TreeMap<>(root).keySet()) {
                Config block = resolved.getConfig(ROOT_PATH + "." + quote(name))
                        .withFallback(defaults);
                LlmConfig config = LlmConfig.fromBlock(name, block);
                built.put(name, buildBundle(config, factories));
            }

            if (built.isEmpty()) {
                throw new ConfigValidationException(
                        "The '" + ROOT_PATH + "' block is empty: no configurations to build");
            }
            return new LlmRegistry(Collections.unmodifiableMap(built));
        }

        private static String quote(String name) {
            return "\"" + name + "\"";
        }

        private static LlmBundle buildBundle(LlmConfig config, Map<String, ProviderFactory> factories) {
            ProviderFactory factory = factories.get(config.provider());
            if (factory == null) {
                List<String> available = new ArrayList<>(factories.keySet());
                Collections.sort(available);
                throw new ConfigValidationException("llm." + config.name()
                        + ".provider is '" + config.provider() + "', for which no provider"
                        + " module is on the classpath. Available providers: "
                        + (available.isEmpty() ? "(none)" : String.join(", ", available)));
            }

            validateCapabilities(config, factory);
            factory.validate(config);

            return new LlmBundle(
                    config,
                    Objects.requireNonNull(
                            factory.createChatModel(config),
                            "createChatModel returned null for llm." + config.name()),
                    config.streaming()
                            ? factory.createStreamingChatModel(config)
                            : Optional.empty(),
                    config.moderationEnabled()
                            ? factory.createModerationModel(config)
                            : Optional.empty(),
                    buildMemoryProvider(config, factory));
        }

        /**
         * Applies the capability rules that depend only on what the factory reports, so no
         * provider module has to restate them.
         */
        private static void validateCapabilities(LlmConfig config, ProviderFactory factory) {
            if (config.memory().isEmpty()
                    || !(config.memory().get() instanceof MemoryConfig.TokenWindow)) {
                return;
            }
            MemoryConfig.TokenWindow window = (MemoryConfig.TokenWindow) config.memory().get();
            TokenEstimation estimation = Objects.requireNonNull(
                    factory.tokenEstimation(),
                    "tokenEstimation returned null for provider " + config.provider());

            if (estimation == TokenEstimation.ABSENT) {
                throw new ConfigValidationException("llm." + config.name()
                        + " uses memory.type = token-window, but provider '" + config.provider()
                        + "' ships no token count estimator, so token-window memory cannot be"
                        + " built. Use memory.type = message-window instead.");
            }
            // The message names the flag on purpose: a validation error that hides its own
            // escape hatch turns opt-in into outright rejection.
            if (estimation == TokenEstimation.REMOTE && !window.allowRemoteTokenCounting()) {
                throw new ConfigValidationException("llm." + config.name()
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
            if (memory instanceof MemoryConfig.MessageWindow) {
                int maxMessages = ((MemoryConfig.MessageWindow) memory).maxMessages();
                return Optional.of(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(maxMessages)
                        .build());
            }

            int maxTokens = ((MemoryConfig.TokenWindow) memory).maxTokens();
            TokenCountEstimator estimator = factory.createTokenCountEstimator(config)
                    .orElseThrow(() -> new ConfigValidationException("llm." + config.name()
                            + " uses memory.type = token-window, but provider '"
                            + config.provider() + "' supplied no token count estimator"));
            return Optional.of(memoryId -> TokenWindowChatMemory.builder()
                    .id(memoryId)
                    .maxTokens(maxTokens, estimator)
                    .build());
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
}
