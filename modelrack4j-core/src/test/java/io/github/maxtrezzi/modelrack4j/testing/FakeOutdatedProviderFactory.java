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
 * Behaves like a factory compiled against {@code 0.2.0}, before the two requirement methods
 * existed.
 *
 * <p>Such a class cannot be compiled here, because the methods have no default (ADR-0062). The
 * JVM runs it anyway, and the first call to a method it lacks throws
 * {@code AbstractMethodError}; these two methods throw it themselves, which is what core sees.
 * The real case was measured with the released {@code 0.2.0} OpenAI provider, and ended the
 * watcher thread before core translated the error.
 */
public final class FakeOutdatedProviderFactory implements ProviderFactory {

    @Override
    public String providerId() {
        return "fake-outdated";
    }

    @Override
    public KeyRequirement apiKeyRequirement() {
        throw new AbstractMethodError("apiKeyRequirement() is abstract");
    }

    @Override
    public KeyRequirement baseUrlRequirement() {
        throw new AbstractMethodError("baseUrlRequirement() is abstract");
    }

    @Override
    public TokenEstimation tokenEstimation() {
        return TokenEstimation.LOCAL;
    }

    @Override
    public void validate(LlmConfig config) {
        // Never reached: the requirement check fails first.
    }

    @Override
    public ChatModel createChatModel(LlmConfig config) {
        return new FakeProviderFactory.FakeChatModel();
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config) {
        return Optional.empty();
    }

    @Override
    public Optional<ModerationModel> createModerationModel(LlmConfig config) {
        return Optional.empty();
    }

    @Override
    public Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config) {
        return Optional.empty();
    }
}
