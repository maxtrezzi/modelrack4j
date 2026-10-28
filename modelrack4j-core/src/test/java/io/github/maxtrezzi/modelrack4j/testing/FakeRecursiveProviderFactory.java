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
package io.github.maxtrezzi.modelrack4j.testing;

import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.moderation.ModerationModel;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.spi.KeyRequirement;
import io.github.maxtrezzi.modelrack4j.spi.ModelContext;
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.Optional;

/**
 * Follows the SPI's advice for the one-argument {@code create} methods — call the two-argument
 * form with an empty context — and forgets to override the two-argument form.
 *
 * <p>The two-argument defaults call the one-argument methods, so each pair calls the other.
 * The compiler accepts it. Without the guard in the SPI this was a {@code StackOverflowError},
 * which a reload does not catch.
 */
public final class FakeRecursiveProviderFactory implements ProviderFactory {

    @Override
    public String providerId() {
        return "fake-recursive";
    }

    @Override
    public TokenEstimation tokenEstimation() {
        return TokenEstimation.ABSENT;
    }

    @Override
    public KeyRequirement apiKeyRequirement() {
        return KeyRequirement.OPTIONAL;
    }

    @Override
    public KeyRequirement baseUrlRequirement() {
        return KeyRequirement.OPTIONAL;
    }

    @Override
    public boolean supportsModeration() {
        return true;
    }

    @Override
    public void validate(LlmConfig config) {
        // Nothing to reject.
    }

    @Override
    public ChatModel createChatModel(LlmConfig config) {
        return createChatModel(config, ModelContext.empty());
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config) {
        return createStreamingChatModel(config, ModelContext.empty());
    }

    @Override
    public Optional<ModerationModel> createModerationModel(LlmConfig config) {
        return createModerationModel(config, ModelContext.empty());
    }

    @Override
    public Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config) {
        return Optional.empty();
    }
}
