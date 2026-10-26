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
package io.github.maxtrezzi.modelrack4j.provider.mistral;

import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModelName;
import dev.langchain4j.model.mistralai.MistralAiModerationModel;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import dev.langchain4j.model.moderation.ModerationModel;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.spi.KeyRequirement;
import io.github.maxtrezzi.modelrack4j.spi.ModelContext;
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.Optional;

/**
 * Builds Mistral AI models from configuration.
 *
 * <p>Mistral is the second provider with a moderation model, after OpenAI. It has no token
 * counter: the {@code langchain4j-mistral-ai} artifact ships no estimator, so token-window
 * memory is refused for it, as for GLM and Ollama.
 */
public final class MistralProviderFactory implements ProviderFactory {

    /** The value that selects this factory in a block's {@code provider} key. */
    public static final String PROVIDER_ID = "mistral";

    /**
     * The model the moderation endpoint is called with.
     *
     * <p>{@code MistralAiModerationModel} has no default and refuses a blank name when it is
     * built, and the block's {@code model-name} names a chat model, which that endpoint does
     * not take. So this factory chooses, and takes the name from LangChain4j's own enum rather
     * than spelling it here: when upstream renames it, the change arrives with the version
     * bump. It is the model OpenAI's factory leaves to OpenAI's server, which has a default.
     */
    static final String MODERATION_MODEL_NAME =
            MistralAiChatModelName.MISTRAL_MODERATION_LATEST.toString();

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
        // The client calls api.mistral.ai when none is given, and all three builders this
        // factory uses accept another address, for a proxy or a gateway.
        return KeyRequirement.OPTIONAL;
    }

    @Override
    public TokenEstimation tokenEstimation() {
        // Read from the 1.22.0 artifact: no class in it estimates tokens. Core turns this
        // into the refusal of token-window memory.
        return TokenEstimation.ABSENT;
    }

    @Override
    public boolean supportsModeration() {
        // MistralAiModerationModel is in the artifact, and its builder takes the same key and
        // address as the chat model.
        return true;
    }

    @Override
    public void validate(LlmConfig config) {
        // Nothing for this provider to reject: the key rules and both capabilities are
        // reported above and applied by core (ADR-0048).
    }

    @Override
    public ChatModel createChatModel(LlmConfig config) {
        return createChatModel(config, ModelContext.empty());
    }

    @Override
    public ChatModel createChatModel(LlmConfig config, ModelContext context) {
        MistralAiChatModel.MistralAiChatModelBuilder builder = MistralAiChatModel.builder()
                .apiKey(apiKey(config))
                .modelName(config.modelName())
                .timeout(config.timeout())
                .logRequests(config.logRequests())
                .logResponses(config.logResponses());
        builder.listeners(context.chatModelListeners());
        config.temperature().ifPresent(builder::temperature);
        config.baseUrl().ifPresent(builder::baseUrl);
        return builder.build();
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config) {
        return createStreamingChatModel(config, ModelContext.empty());
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(
            LlmConfig config, ModelContext context) {
        MistralAiStreamingChatModel.MistralAiStreamingChatModelBuilder builder =
                MistralAiStreamingChatModel.builder()
                        .apiKey(apiKey(config))
                        .modelName(config.modelName())
                        .timeout(config.timeout())
                        .logRequests(config.logRequests())
                        .logResponses(config.logResponses());
        builder.listeners(context.chatModelListeners());
        config.temperature().ifPresent(builder::temperature);
        config.baseUrl().ifPresent(builder::baseUrl);
        return Optional.of(builder.build());
    }

    @Override
    public Optional<ModerationModel> createModerationModel(LlmConfig config) {
        return createModerationModel(config, ModelContext.empty());
    }

    @Override
    public Optional<ModerationModel> createModerationModel(
            LlmConfig config, ModelContext context) {
        // Not config.modelName(): that is a chat model. See MODERATION_MODEL_NAME.
        MistralAiModerationModel.Builder builder = MistralAiModerationModel.builder()
                .apiKey(apiKey(config))
                .modelName(MODERATION_MODEL_NAME)
                .timeout(config.timeout())
                .logRequests(config.logRequests())
                .logResponses(config.logResponses());
        builder.listeners(context.moderationModelListeners());
        // The same address as the chat model: a block behind a proxy must not send its
        // moderation to the vendor's own address (ADR-0062).
        config.baseUrl().ifPresent(builder::baseUrl);
        return Optional.of(builder.build());
    }

    @Override
    public Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config) {
        // Unreachable through the registry, which refuses token-window memory for this
        // provider first. Empty rather than an exception, so the SPI contract holds if it is
        // called directly.
        return Optional.empty();
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
