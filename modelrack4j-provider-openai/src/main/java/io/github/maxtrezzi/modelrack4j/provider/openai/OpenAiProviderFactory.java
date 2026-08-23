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
package io.github.maxtrezzi.modelrack4j.provider.openai;

import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import io.github.maxtrezzi.modelrack4j.ConfigValidationException;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.Optional;

/**
 * Builds OpenAI models from configuration.
 *
 * <p>OpenAI is the only provider in v1 that supplies every capability the configuration can
 * ask for: it moderates, and it counts tokens in-process rather than over the network.
 */
public final class OpenAiProviderFactory implements ProviderFactory {

    /** The value that selects this factory in a block's {@code provider} key. */
    public static final String PROVIDER_ID = "openai";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public TokenEstimation tokenEstimation() {
        // jtokkit is a compile dependency of langchain4j-open-ai, so counting is in-process
        // and free. This is what makes token-window memory unremarkable here.
        return TokenEstimation.LOCAL;
    }

    @Override
    public void validate(LlmConfig config) {
        // Nothing to reject: OpenAI supplies every capability the schema can request. The
        // method is deliberately empty rather than absent, so a future capability gap has an
        // obvious home.
    }

    @Override
    public ChatModel createChatModel(LlmConfig config) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.modelName())
                .timeout(config.timeout())
                .logRequests(config.logRequests())
                .logResponses(config.logResponses());
        config.temperature().ifPresent(builder::temperature);
        return builder.build();
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config) {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder =
                OpenAiStreamingChatModel.builder()
                        .apiKey(config.apiKey())
                        .modelName(config.modelName())
                        .timeout(config.timeout())
                        .logRequests(config.logRequests())
                        .logResponses(config.logResponses());
        config.temperature().ifPresent(builder::temperature);
        return Optional.of(builder.build());
    }

    @Override
    public Optional<ModerationModel> createModerationModel(LlmConfig config) {
        // modelName is deliberately NOT passed through: it names a chat model, and OpenAI's
        // moderation endpoint takes its own separate model. Forwarding the chat model name
        // here would send a request the API rejects.
        return Optional.of(OpenAiModerationModel.builder()
                .apiKey(config.apiKey())
                .timeout(config.timeout())
                .logRequests(config.logRequests())
                .logResponses(config.logResponses())
                .build());
    }

    @Override
    public Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config) {
        try {
            return Optional.of(new OpenAiTokenCountEstimator(config.modelName()));
        } catch (RuntimeException e) {
            // Local counting needs a tokenizer encoding for the model name, and jtokkit only
            // knows the models it shipped with. A model newer than the pinned jtokkit is the
            // normal way to reach this, so it must name the cause rather than surface as an
            // unrelated failure deep inside memory eviction.
            throw new ConfigValidationException("llm." + config.name()
                    + " uses memory.type = token-window, but no local tokenizer is known for"
                    + " OpenAI model '" + config.modelName() + "'. Use memory.type ="
                    + " message-window, or a model the bundled tokenizer recognises.", e);
        }
    }
}
