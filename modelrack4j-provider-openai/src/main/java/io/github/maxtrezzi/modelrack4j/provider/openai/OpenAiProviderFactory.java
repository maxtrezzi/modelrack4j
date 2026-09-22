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
import io.github.maxtrezzi.modelrack4j.spi.KeyRequirement;
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.Optional;

/**
 * Builds OpenAI models from configuration.
 *
 * <p>OpenAI is the only provider in v1 that supplies every capability the configuration can
 * ask for: it moderates, and it counts tokens in-process rather than over the network.
 *
 * <p>It is also the provider that reaches servers other than the vendor's. With a
 * {@code base-url}, its client talks to any server that speaks the OpenAI protocol — LocalAI,
 * llama.cpp, vLLM, LM Studio, or Ollama's compatible endpoint — and such a server often needs
 * no key, so {@code api-key} is optional here (ADR-0062).
 */
public final class OpenAiProviderFactory implements ProviderFactory {

    /** The value that selects this factory in a block's {@code provider} key. */
    public static final String PROVIDER_ID = "openai";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public KeyRequirement apiKeyRequirement() {
        // Not MANDATORY: the client sends an Authorization header only when it has a key, and
        // a local server behind base-url often takes none. The one case that does need a key
        // is refused in validate(), because it depends on base-url.
        return KeyRequirement.OPTIONAL;
    }

    @Override
    public KeyRequirement baseUrlRequirement() {
        // Absent means api.openai.com, the client's own default.
        return KeyRequirement.OPTIONAL;
    }

    @Override
    public TokenEstimation tokenEstimation() {
        // jtokkit is a compile dependency of langchain4j-open-ai, so counting is in-process
        // and free. This is what makes token-window memory unremarkable here.
        return TokenEstimation.LOCAL;
    }

    @Override
    public boolean supportsModeration() {
        // Stated rather than left to the default. The default is true because that is what
        // keeps an older third-party factory working, not because it asserts anything; this
        // one is the provider that genuinely moderates, and it should say so.
        return true;
    }

    @Override
    public void validate(LlmConfig config) {
        // The rule core cannot see (ADR-0062): whether a key is needed depends on where the
        // block points, and only this factory knows that no base-url means api.openai.com.
        if (config.apiKey().isEmpty() && config.baseUrl().isEmpty()) {
            throw new ConfigValidationException("llm." + config.name()
                    + " has neither api-key nor base-url. Provider '" + PROVIDER_ID
                    + "' calls api.openai.com when base-url is absent, and that endpoint"
                    + " requires a key. Set api-key, or set base-url to a server that does not"
                    + " need one.");
        }
    }

    @Override
    public ChatModel createChatModel(LlmConfig config) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .modelName(config.modelName())
                .timeout(config.timeout())
                .logRequests(config.logRequests())
                .logResponses(config.logResponses());
        config.temperature().ifPresent(builder::temperature);
        config.apiKey().ifPresent(builder::apiKey);
        config.baseUrl().ifPresent(builder::baseUrl);
        return builder.build();
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config) {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder =
                OpenAiStreamingChatModel.builder()
                        .modelName(config.modelName())
                        .timeout(config.timeout())
                        .logRequests(config.logRequests())
                        .logResponses(config.logResponses());
        config.temperature().ifPresent(builder::temperature);
        config.apiKey().ifPresent(builder::apiKey);
        config.baseUrl().ifPresent(builder::baseUrl);
        return Optional.of(builder.build());
    }

    @Override
    public Optional<ModerationModel> createModerationModel(LlmConfig config) {
        // modelName is deliberately NOT passed through: it names a chat model, and OpenAI's
        // moderation endpoint takes its own separate model. Forwarding the chat model name
        // here would send a request the API rejects.
        OpenAiModerationModel.OpenAiModerationModelBuilder builder =
                OpenAiModerationModel.builder()
                        .timeout(config.timeout())
                        .logRequests(config.logRequests())
                        .logResponses(config.logResponses());
        config.apiKey().ifPresent(builder::apiKey);
        // The same address as the chat model: a block behind a proxy must not send its
        // moderation to the vendor's own address (ADR-0062).
        config.baseUrl().ifPresent(builder::baseUrl);
        return Optional.of(builder.build());
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
