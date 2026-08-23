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
package io.github.maxtrezzi.modelrack4j.provider.glm;

import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.community.model.zhipu.ZhipuAiStreamingChatModel;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.moderation.ModerationModel;
import io.github.maxtrezzi.modelrack4j.ConfigValidationException;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.Optional;

/**
 * Builds Zhipu GLM models from configuration, on the community
 * {@code langchain4j-community-zhipu-ai} module.
 *
 * <p>This is the narrowest provider in v1: a chat model and a streaming chat model, and
 * nothing else. The module ships no {@code ModerationModel} and no
 * {@code TokenCountEstimator} of any kind, so token-window memory is unavailable here rather
 * than merely expensive.
 *
 * @implNote Two shape differences from the other three modules are handled here rather than
 *     leaking into the configuration schema: the builder names the model {@code model}
 *     instead of {@code modelName}, and it has no single request timeout.
 */
public final class GlmProviderFactory implements ProviderFactory {

    /** The value that selects this factory in a block's {@code provider} key. */
    public static final String PROVIDER_ID = "glm";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public TokenEstimation tokenEstimation() {
        // Read from the artifact, not assumed: the module contains no TokenCountEstimator
        // implementation at all. Core turns ABSENT into a rejection that points at
        // message-window memory, which every provider can do.
        return TokenEstimation.ABSENT;
    }

    @Override
    public void validate(LlmConfig config) {
        if (config.moderationEnabled()) {
            throw new ConfigValidationException("llm." + config.name()
                    + " sets moderation.enabled = true, but provider 'glm' ships no"
                    + " moderation model. Remove the moderation block, or route moderation"
                    + " through an OpenAI-family configuration.");
        }
    }

    @Override
    public ChatModel createChatModel(LlmConfig config) {
        ZhipuAiChatModel.ZhipuAiChatModelBuilder builder = ZhipuAiChatModel.builder()
                .apiKey(config.apiKey())
                .model(config.modelName())
                .connectTimeout(config.timeout())
                .readTimeout(config.timeout())
                .logRequests(config.logRequests())
                .logResponses(config.logResponses());
        config.temperature().ifPresent(builder::temperature);
        return builder.build();
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config) {
        ZhipuAiStreamingChatModel.ZhipuAiStreamingChatModelBuilder builder =
                ZhipuAiStreamingChatModel.builder()
                        .apiKey(config.apiKey())
                        .model(config.modelName())
                        .connectTimeout(config.timeout())
                        .readTimeout(config.timeout())
                        .logRequests(config.logRequests())
                        .logResponses(config.logResponses());
        config.temperature().ifPresent(builder::temperature);
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
        // Nothing to build. Core never calls this for an ABSENT provider, and returning
        // empty keeps that consistent if it is called directly.
        return Optional.empty();
    }
}
