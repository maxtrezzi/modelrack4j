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
package io.github.maxtrezzi.modelrack4j.provider.gemini;

import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiTokenCountEstimator;
import dev.langchain4j.model.moderation.ModerationModel;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.Optional;

/**
 * Builds Gemini models from configuration, on the stable {@code langchain4j-google-ai-gemini}
 * module.
 *
 * <p>Capabilities match Anthropic's rather than OpenAI's: no {@code ModerationModel}, and a
 * token count estimator that is an HTTP client rather than a local tokenizer.
 */
public final class GeminiProviderFactory implements ProviderFactory {

    /** The value that selects this factory in a block's {@code provider} key. */
    public static final String PROVIDER_ID = "gemini";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public TokenEstimation tokenEstimation() {
        // GoogleAiGeminiTokenCountEstimator is built from an apiKey, baseUrl and timeout, and
        // calls the countTokens endpoint. Core turns this into the opt-in rule.
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
        GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder =
                GoogleAiGeminiChatModel.builder()
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
        GoogleAiGeminiStreamingChatModel.GoogleAiGeminiStreamingChatModelBuilder builder =
                GoogleAiGeminiStreamingChatModel.builder()
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
        // Unreachable through the registry, which calls validate() first. Empty rather than
        // an exception, so the SPI contract holds if it is ever called directly.
        return Optional.empty();
    }

    @Override
    public Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config) {
        return Optional.of(GoogleAiGeminiTokenCountEstimator.builder()
                .apiKey(config.apiKey())
                .modelName(config.modelName())
                .timeout(config.timeout())
                .logRequests(config.logRequests())
                .logResponses(config.logResponses())
                .build());
    }
}
