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
package io.github.maxtrezzi.modelrack4j.provider.ollama;

import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.spi.KeyRequirement;
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.Optional;

/**
 * Builds Ollama models from configuration, on the stable {@code langchain4j-ollama} module.
 *
 * <p>Ollama is a server the user runs, not a vendor's service, and that shapes both of its
 * key requirements: it takes no credential, so {@code api-key} is forbidden, and it has no
 * address anyone could assume, so {@code base-url} is required (ADR-0062).
 *
 * <p>Its capabilities are GLM's: a chat model and a streaming chat model, and nothing else. The
 * module ships no {@code ModerationModel} and no {@code TokenCountEstimator}, so token-window
 * memory is unavailable here, and message-window memory is the way to bound a conversation.
 */
public final class OllamaProviderFactory implements ProviderFactory {

    /** The value that selects this factory in a block's {@code provider} key. */
    public static final String PROVIDER_ID = "ollama";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public KeyRequirement apiKeyRequirement() {
        // Read from the artifact: neither builder has an apiKey method, so a key in the block
        // would be silently unused. Refusing it tells the user so at load time.
        return KeyRequirement.FORBIDDEN;
    }

    @Override
    public KeyRequirement baseUrlRequirement() {
        // OllamaClient calls ensureNotBlank(baseUrl, "baseUrl"), and the jar contains no
        // default address. Core refuses a block without one, in its own words, before the
        // builder would.
        return KeyRequirement.MANDATORY;
    }

    @Override
    public TokenEstimation tokenEstimation() {
        // Read from the artifact, not assumed: the module contains no TokenCountEstimator.
        // Core turns ABSENT into a rejection that points at message-window memory.
        return TokenEstimation.ABSENT;
    }

    @Override
    public boolean supportsModeration() {
        // Read from the artifact, not assumed: the module ships no ModerationModel.
        return false;
    }

    @Override
    public void validate(LlmConfig config) {
        // Nothing left to reject here: the missing key, the missing address and both missing
        // capabilities are all reported to core and refused there.
    }

    @Override
    public ChatModel createChatModel(LlmConfig config) {
        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder()
                .baseUrl(baseUrl(config))
                .modelName(config.modelName())
                .timeout(config.timeout())
                .logRequests(config.logRequests())
                .logResponses(config.logResponses());
        config.temperature().ifPresent(builder::temperature);
        return builder.build();
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config) {
        OllamaStreamingChatModel.OllamaStreamingChatModelBuilder builder =
                OllamaStreamingChatModel.builder()
                        .baseUrl(baseUrl(config))
                        .modelName(config.modelName())
                        .timeout(config.timeout())
                        .logRequests(config.logRequests())
                        .logResponses(config.logResponses());
        config.temperature().ifPresent(builder::temperature);
        return Optional.of(builder.build());
    }

    @Override
    public Optional<ModerationModel> createModerationModel(LlmConfig config) {
        // Unreachable through the registry, which refuses moderation for this provider first.
        // Empty rather than an exception, so the SPI contract holds if it is called directly.
        return Optional.empty();
    }

    @Override
    public Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config) {
        // Nothing to build. Core never calls this for an ABSENT provider.
        return Optional.empty();
    }

    /**
     * Returns the block's address.
     *
     * @throws IllegalArgumentException if the block has none, which only a caller that
     *     bypasses the registry can arrange
     * @implNote Never throws through the registry: {@link #baseUrlRequirement()} is
     *     {@code MANDATORY}, so core has refused a block without one before any method here is
     *     called.
     */
    private static String baseUrl(LlmConfig config) {
        return config.baseUrl().orElseThrow(() -> new IllegalArgumentException("llm."
                + config.name() + " has no base-url, and provider '" + PROVIDER_ID
                + "' requires one"));
    }
}
