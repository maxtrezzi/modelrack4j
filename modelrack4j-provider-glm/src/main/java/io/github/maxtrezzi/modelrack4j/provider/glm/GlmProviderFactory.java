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
import java.nio.charset.StandardCharsets;
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
 * <p>It is also the one provider with a rule about the {@code api-key} itself. A GLM key has
 * the form {@code id.secret}, and the provider signs a token with the second part instead of
 * sending the key. {@link #validate(LlmConfig)} therefore rejects a key of another shape when
 * the configuration loads, rather than letting it fail on the first request.
 *
 * @implNote Two shape differences from the other three modules are handled here rather than
 *     leaking into the configuration schema: the builder names the model {@code model}
 *     instead of {@code modelName}, and it has no single request timeout.
 */
public final class GlmProviderFactory implements ProviderFactory {

    /** The value that selects this factory in a block's {@code provider} key. */
    public static final String PROVIDER_ID = "glm";

    /**
     * The shortest secret HS256 accepts, in bytes. RFC 7518 section 3.2 requires a key at
     * least as long as the hash output, and the JWT library the provider uses enforces it.
     */
    private static final int MIN_SECRET_BYTES = 16;

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
    public boolean supportsModeration() {
        // Read from the artifact, not assumed: the module ships no ModerationModel. Core
        // turns this into the rejection, so the message is the same for every provider that
        // cannot moderate.
        return false;
    }

    @Override
    public void validate(LlmConfig config) {
        // The rule core cannot see (ADR-0048). This provider does not send the key: it
        // splits it on '.' and signs a JWT with the second half, so a key of the wrong
        // shape fails while the first request is assembled. Those failures are JDK and JWT
        // exceptions, outside the LangChain4jException family applications are told to
        // catch, and none of them names a key. Rejecting the shape here turns all three
        // into one ConfigValidationException at load time.
        String[] parts = config.apiKey().split("\\.");
        if (parts.length < 2) {
            throw rejected(config, "it has no '.', so it has no secret part");
        }
        // Byte length, not character count: the provider signs with the UTF-8 bytes.
        int secretBytes = parts[1].getBytes(StandardCharsets.UTF_8).length;
        if (secretBytes < MIN_SECRET_BYTES) {
            // The length of a secret that is already invalid is safe to print, and it
            // separates a truncated key from a placeholder. The key itself never is
            // (ADR-0047).
            throw rejected(config, "its secret part is " + secretBytes + " bytes, and HS256"
                    + " signing needs at least " + MIN_SECRET_BYTES);
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

    /**
     * Builds the one rejection message, so the two shape failures read alike and neither can
     * drift. The message names the block and the requirement, never the key.
     */
    private static ConfigValidationException rejected(LlmConfig config, String because) {
        return new ConfigValidationException("llm." + config.name()
                + ".api-key is not shaped like a GLM key: " + because + ". A GLM key has the"
                + " form id.secret, and provider 'glm' builds its authorisation token from"
                + " both parts, so a key of another shape fails while a request is"
                + " assembled, before any call is made.");
    }
}
