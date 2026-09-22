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
package io.github.maxtrezzi.modelrack4j.provider.anthropic;

import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.anthropic.AnthropicTokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.moderation.ModerationModel;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.spi.KeyRequirement;
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.Optional;

/**
 * Builds Anthropic models from configuration.
 *
 * <p>Two capability facts shape this factory, both confirmed against the pinned artifacts
 * rather than assumed: Anthropic ships <strong>no</strong> {@code ModerationModel}, and its
 * token count estimator is an HTTP client, so counting is billed and can fail.
 */
public final class AnthropicProviderFactory implements ProviderFactory {

    /** The value that selects this factory in a block's {@code provider} key. */
    public static final String PROVIDER_ID = "anthropic";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public KeyRequirement apiKeyRequirement() {
        // The vendor's API refuses a request without one, so a block that forgot its key
        // should fail when it loads rather than on its first request (ADR-0062).
        return KeyRequirement.MANDATORY;
    }

    @Override
    public KeyRequirement baseUrlRequirement() {
        // The client calls the vendor's own address when none is given, and every builder
        // this factory uses accepts another one, for a proxy or a gateway.
        return KeyRequirement.OPTIONAL;
    }

    @Override
    public TokenEstimation tokenEstimation() {
        // AnthropicTokenCountEstimator is built from an apiKey, baseUrl and timeout: it is an
        // HTTP client, not a local tokenizer. Core turns this into the opt-in rule.
        return TokenEstimation.REMOTE;
    }

    @Override
    public boolean supportsModeration() {
        // Read from the artifact, not assumed: the module ships no ModerationModel. Core
        // turns this into the rejection, so the message is the same for every provider that
        // cannot moderate.
        return false;
    }

    @Override
    public void validate(LlmConfig config) {
        // Nothing left to reject here: the one capability this provider lacks is reported
        // by supportsModeration() and refused by core. The method stays, empty, so a future
        // gap that core cannot see has an obvious home.
    }

    @Override
    public ChatModel createChatModel(LlmConfig config) {
        AnthropicChatModel.AnthropicChatModelBuilder builder = AnthropicChatModel.builder()
                .apiKey(apiKey(config))
                .modelName(config.modelName())
                .timeout(config.timeout())
                .logRequests(config.logRequests())
                .logResponses(config.logResponses());
        config.temperature().ifPresent(builder::temperature);
        config.baseUrl().ifPresent(builder::baseUrl);
        return builder.build();
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config) {
        AnthropicStreamingChatModel.AnthropicStreamingChatModelBuilder builder =
                AnthropicStreamingChatModel.builder()
                        .apiKey(apiKey(config))
                        .modelName(config.modelName())
                        .timeout(config.timeout())
                        .logRequests(config.logRequests())
                        .logResponses(config.logResponses());
        config.temperature().ifPresent(builder::temperature);
        config.baseUrl().ifPresent(builder::baseUrl);
        return Optional.of(builder.build());
    }

    @Override
    public Optional<ModerationModel> createModerationModel(LlmConfig config) {
        // Unreachable through the registry, which calls validate() first. Empty rather than
        // an exception, so the SPI contract holds if it is ever called directly.
        return Optional.empty();
    }

    @Override
    public Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config) {
        AnthropicTokenCountEstimator.Builder builder = AnthropicTokenCountEstimator.builder()
                .apiKey(apiKey(config))
                .modelName(config.modelName())
                .timeout(config.timeout())
                .logRequests(config.logRequests())
                .logResponses(config.logResponses());
        // The same address as the chat model: a block behind a proxy must not count its
        // tokens at the vendor's own address (ADR-0062).
        config.baseUrl().ifPresent(builder::baseUrl);
        return Optional.of(builder.build());
    }

    /**
     * Returns the block's key.
     *
     * @throws IllegalArgumentException if the block has none, which only a caller that
     *     bypasses the registry can arrange
     * @implNote Never throws through the registry: {@link #apiKeyRequirement()} is
     *     {@code MANDATORY}, so core has refused a block without a key before any method here
     *     is called.
     */
    private static String apiKey(LlmConfig config) {
        return config.apiKey().orElseThrow(() -> new IllegalArgumentException("llm."
                + config.name() + " has no api-key, and provider '" + PROVIDER_ID
                + "' requires one"));
    }
}
