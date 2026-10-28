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
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.Optional;

/**
 * A factory written for {@code 0.3.0}, which builds every model and knows nothing of
 * {@code ModelContext}.
 *
 * <p>It implements only the one-argument {@code create} methods, so core reaches them through
 * the SPI's defaults (ADR-0063). Unlike {@link FakeLegacyProviderFactory} it produces a
 * streaming and a moderation model, so a test can see that each default delegates rather than
 * returning nothing.
 */
public final class FakePreContextProviderFactory implements ProviderFactory {

    @Override
    public String providerId() {
        return "fake-pre-context";
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
        return new FakeProviderFactory.FakeChatModel();
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config) {
        return Optional.of(new FakeProviderFactory.FakeStreamingChatModel());
    }

    @Override
    public Optional<ModerationModel> createModerationModel(LlmConfig config) {
        return Optional.of(new FakeProviderFactory.FakeModerationModel());
    }

    @Override
    public Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config) {
        return Optional.empty();
    }
}
