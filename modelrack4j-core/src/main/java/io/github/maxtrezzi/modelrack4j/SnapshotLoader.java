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
import com.typesafe.config.ConfigValueType;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import io.github.maxtrezzi.modelrack4j.spi.KeyRequirement;
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
import java.util.function.Supplier;

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
final class SnapshotLoader<T> {

    /** Root path holding the named blocks. */
    static final String ROOT_PATH = "llm";

    private final List<Layer> layers;
    private final Map<String, ProviderFactory> factories;

    /** What turns a block's custom properties into the application's own object; may be null. */
    private final CustomPropertiesHandler<T> customPropertiesHandler;

    SnapshotLoader(List<Layer> layers, CustomPropertiesHandler<T> customPropertiesHandler) {
        this.layers = List.copyOf(layers);
        this.factories = discoverFactories();
        this.customPropertiesHandler = customPropertiesHandler;
    }

    /**
     * Parses every layer and returns the snapshot it describes.
     *
     * @param previous the live snapshot, whose bundles are carried over where the parsed
     *     configuration is unchanged; empty on the first load
     * @return a complete snapshot, sorted by name, which may be empty when no layer defines a
     *     configuration (ADR-0057)
     * @throws ConfigValidationException if any block is invalid, carries a key the schema does
     *     not know, is set to {@code null}, or any provider rejects or fails to build its
     *     configuration
     * @throws ConfigAccessException if any layer cannot be read
     */
    Map<String, LlmBundle<T>> load(Map<String, LlmBundle<T>> previous) {
        return load(previous, layers);
    }

    /**
     * Parses the given layers and returns the snapshot they describe.
     *
     * @param previous the live snapshot, whose bundles are carried over where the parsed
     *     configuration is unchanged
     * @param layers the layers to read, which during a store is this registry's list with
     *     the layer being written replaced by its staged text
     * @return a complete snapshot, sorted by name, which may be empty when no layer defines a
     *     configuration (ADR-0057)
     * @throws ConfigValidationException if any block is invalid, carries a key the schema does
     *     not know, is set to {@code null}, or any provider rejects or fails to build its
     *     configuration
     * @throws ConfigAccessException if any layer cannot be read
     */
    Map<String, LlmBundle<T>> load(Map<String, LlmBundle<T>> previous, List<Layer> layers) {
        Config resolved = ConfigLoader.load(layers);
        ConfigValue rootValue = resolved.root().get(ROOT_PATH);
        if (rootValue == null) {
            // No layer mentions the block at all. That is a registry with nothing in it,
            // which ADR-0057 makes a valid result rather than a failure: whether an empty
            // configuration is a problem depends on what the application needs from it.
            return Map.of();
        }
        if (rootValue.valueType() == ConfigValueType.NULL) {
            throw removalRefused(ROOT_PATH, rootValue);
        }
        if (!(rootValue instanceof ConfigObject root)) {
            // Otherwise getObject below throws Typesafe Config's own WrongType, which
            // escapes as a foreign exception type from a method documented to throw this
            // library's.
            throw new ConfigValidationException("'" + ROOT_PATH + "' must be a block of named"
                    + " configurations, but is of type " + rootValue.valueType() + " ("
                    + rootValue.origin().description() + ")");
        }

        Config defaults = ConfigLoader.defaults();

        Map<String, LlmBundle<T>> built = new TreeMap<>();
        // Sorted so that when several blocks are invalid, which one is reported is stable
        // between runs instead of following map iteration order.
        for (String name : new TreeSet<>(root.keySet())) {
            ConfigValue value = root.get(name);
            if (value.valueType() == ConfigValueType.NULL) {
                throw removalRefused(ROOT_PATH + "." + name, value);
            }
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
            LlmBundle<T> carried = previous.get(name);
            built.put(name, carried != null && carried.config().equals(config)
                    ? carried
                    : buildBundleReportingLinkage(config));
        }

        return Collections.unmodifiableMap(built);
    }

    /**
     * Refuses {@code = null} where a configuration is expected.
     *
     * @param path what was set to null, as the file spells it
     * @param value the null itself, which carries the layer and line that wrote it
     * @return the exception to throw
     * @implNote ADR-0058. The idiom is real — ADR-0032 clears {@code description} from a
     *     higher layer this way — so a reader will try it one level up and has to be told why
     *     it does not extend. Both {@code llm} and {@code llm.<name>} are refused in the same
     *     words: once an empty registry is legal, nulling the root would otherwise be a
     *     working version of the removal the other refusal declines. A key that is simply
     *     absent is not a removal and stays legal.
     */
    private static ConfigValidationException removalRefused(String path, ConfigValue value) {
        return new ConfigValidationException(path + " is set to null ("
                + value.origin().description() + "). A configuration cannot be removed from a"
                + " higher layer: null clears a value inside a block, not the block itself."
                + " Remove it from the layer that defines it instead.");
    }

    /**
     * Builds one bundle, and turns a provider that cannot run against the classes on the
     * classpath into a configuration error.
     *
     * @param config the configuration to build
     * @return the bundle
     * @throws ConfigValidationException if the configuration is refused, or if the provider's
     *     jar does not match the classes it runs against
     * @implNote A {@code LinkageError} — a {@code NoSuchMethodError} or
     *     {@code NoClassDefFoundError} from a provider jar built against another version of
     *     this library or of LangChain4j — is an {@code Error}, and {@code LlmRegistry.reload()}
     *     and the watcher loop catch only {@code RuntimeException}. Left alone it escapes a
     *     reload with no log line and no failure listener, and on the watcher thread it ends
     *     the thread, so every later edit is ignored. That was measured for one subclass,
     *     {@code AbstractMethodError}, with the released {@code 0.2.0} OpenAI provider; the
     *     path is the same for all of them. Translating is safe here: a linkage error is fixed
     *     by the classes that were loaded, it does not signal a broken VM, and it concerns this
     *     one provider. {@link #requirementOf} catches the most likely case first, with a
     *     message that names the method.
     */
    private LlmBundle<T> buildBundleReportingLinkage(LlmConfig config) {
        try {
            return buildBundle(config);
        } catch (LinkageError mismatched) {
            throw new ConfigValidationException(path(config) + ": provider '"
                    + config.provider() + "' cannot run against the classes on the classpath ("
                    + mismatched + "). Its jar was probably built for another version of"
                    + " modelrack4j or LangChain4j: use the versions the modelrack4j BOM"
                    + " manages.", mismatched);
        }
    }

    private LlmBundle<T> buildBundle(LlmConfig config) {
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

        return new LlmBundle<>(
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
                buildMemoryProvider(config, factory),
                buildCustomProperties(config));
    }

    /**
     * Runs the application's handler, if it registered one.
     *
     * @param config the configuration being built, which carries the block as text
     * @return whatever the handler returned, or {@code null} when there is no handler — which
     *     is then the only value {@code T} has, because the registry is an
     *     {@code LlmRegistry<Void>}
     * @throws ConfigValidationException if the handler rejects the configuration, or returns
     *     {@code null} instead of an object
     * @implNote ADR-0055. The handler runs here rather than after {@code fromBlock} so that it
     *     sees a provider that exists and rules the provider has already accepted, and before
     *     {@code createChatModel} so that a rejected configuration does not first build a model
     *     that is then discarded. It is called for every configuration, including one whose
     *     file has no such block, which arrives as {@code "{}"}: a rule of the form "an openai
     *     block needs a prompt id" is broken exactly in that case.
     *     <p>Whatever it throws is wrapped, and unconditionally. The interface declares
     *     {@code throws Exception}, so a checked exception has to become something this
     *     library's API declares; and wrapping only the unexpected types would make the block's
     *     name appear in the message or not depending on how careful the caller had been.
     */
    private T buildCustomProperties(LlmConfig config) {
        if (customPropertiesHandler == null) {
            return null;
        }
        T properties;
        try {
            properties = customPropertiesHandler.handle(config);
        } catch (InterruptedException interrupted) {
            // The handler declares throws Exception, so this one can arrive. Wrapping it
            // without restoring the flag would discard a cancellation the caller's thread is
            // waiting on, and this runs on the caller's thread during build() and reload().
            Thread.currentThread().interrupt();
            throw new ConfigValidationException(path(config) + ": the custom-properties handler"
                    + " was interrupted", interrupted);
        } catch (Exception rejected) {
            // getMessage() rather than the exception, so the sentence reads as one: an
            // application's own "max-retries is 99" belongs in this message, its class name
            // does not. A rejection thrown with no message falls back to the class name,
            // because "rejected this configuration: null" names nothing at all.
            String said = rejected.getMessage() == null
                    ? rejected.getClass().getName()
                    : rejected.getMessage();
            throw new ConfigValidationException(path(config) + ": the custom-properties handler"
                    + " rejected this configuration: " + said, rejected);
        }
        if (properties == null) {
            // Through the same fail-fast as a factory that produces no model: a handler that
            // returns nothing leaves customProperties() null on a bundle whose type says it is
            // there, and the application meets it as a NullPointerException far from here.
            throw new ConfigValidationException(path(config) + ": the custom-properties handler"
                    + " returned null. Return an object, or throw to reject the configuration.");
        }
        return properties;
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
    private static <M> Optional<M> requireProduced(
            Optional<M> produced, LlmConfig config, String requestedBy, String what) {
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
     *
     * @implNote The two key requirements come first and apply to every block, because a block
     *     that sets a key its provider does not use, or omits one it needs, is wrong whatever
     *     else it asks for (ADR-0062). They run before the factory's own {@code validate()},
     *     which may therefore rely on them.
     */
    private static void validateCapabilities(LlmConfig config, ProviderFactory factory) {
        requireKey(config, "api-key", config.apiKey(),
                requirementOf(config, "api-key", factory::apiKeyRequirement));
        requireKey(config, "base-url", config.baseUrl(),
                requirementOf(config, "base-url", factory::baseUrlRequirement));

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

    /**
     * Asks the factory for one key requirement, and turns a factory built for an older SPI
     * into a configuration error.
     *
     * @param config the configuration being built, for the message
     * @param key the key as the file spells it, for the message
     * @param reported the factory method that reports the requirement
     * @return what the factory reported, which may be null from a faulty factory
     * @throws ConfigValidationException if the factory does not implement the method
     * @implNote The two methods have no default (ADR-0062), so a factory compiled against
     *     {@code 0.2.0} throws {@code AbstractMethodError} here. That is an {@code Error}, and
     *     both {@code LlmRegistry.reload()} and the watcher loop catch only
     *     {@code RuntimeException}: left alone, it escaped a reload with no log line and no
     *     failure listener, and on the watcher thread it ended the thread, so every later
     *     edit was ignored in silence. Measured with the released {@code 0.2.0} OpenAI
     *     provider, added by a reload to a registry that had started without it. Translating
     *     it is safe: a linkage error is fixed by the class that was loaded, not a sign of a
     *     broken VM, and it can only mean this one factory is out of date.
     */
    private static KeyRequirement requirementOf(
            LlmConfig config, String key, Supplier<KeyRequirement> reported) {
        try {
            return reported.get();
        } catch (AbstractMethodError builtForAnOlderVersion) {
            throw new ConfigValidationException(path(config) + ": provider '"
                    + config.provider() + "' does not implement the " + key + " requirement,"
                    + " so it was built for an older modelrack4j. Rebuild it against this"
                    + " version.", builtForAnOlderVersion);
        }
    }

    /**
     * Refuses a key the provider does not permit, or a missing key it requires.
     *
     * @param config the configuration being built
     * @param key the key as the file spells it, for the message
     * @param value what the block set for it
     * @param requirement what the factory reported, which may be null from a faulty factory
     * @throws ConfigValidationException if the block breaks the requirement
     */
    private static void requireKey(LlmConfig config, String key, Optional<String> value,
            KeyRequirement requirement) {
        if (requirement == null) {
            throw new ConfigValidationException(path(config) + ": provider '"
                    + config.provider() + "' reported no " + key + " requirement, so whether"
                    + " this block may set " + key + " cannot be decided.");
        }
        if (value.isPresent() && !requirement.permitted()) {
            throw new ConfigValidationException(path(config) + " sets " + key
                    + ", but provider '" + config.provider() + "' does not use one. Remove "
                    + key + " from this block.");
        }
        if (value.isEmpty() && requirement.required()) {
            throw new ConfigValidationException(path(config) + " has no " + key
                    + ", but provider '" + config.provider() + "' requires one. Set " + key
                    + " in this block.");
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
            // Through requireProduced like the other two capabilities, rather than a bespoke
            // orElseThrow. A factory that returns null instead of an empty Optional breaks
            // the SPI the same way for all three, and this was the one call site that turned
            // it into a bare NullPointerException instead of naming the provider.
            //
            // get() is safe here and nowhere else: requireProduced throws on both null and
            // empty, so what it returns is always present.
            TokenCountEstimator estimator = requireProduced(
                    factory.createTokenCountEstimator(config), config,
                    "memory.type = token-window", "token count estimator").get();
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
