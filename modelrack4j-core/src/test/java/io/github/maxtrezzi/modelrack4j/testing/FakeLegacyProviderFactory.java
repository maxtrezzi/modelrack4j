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
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.Optional;

/**
 * A factory written before {@code supportsModeration()} existed, and never updated.
 *
 * <p>This is the compatibility case ADR-0048 turns on, so it is a real
 * {@link ProviderFactory} rather than a subclass of {@link FakeProviderFactory}: that base
 * class re-declares the capability method as abstract, which would force the override this
 * fake exists to leave out. Everything here is the minimum the SPI requires, with
 * {@code supportsModeration()} deliberately absent so the interface's default applies.
 *
 * <p>What it pins: such a factory still builds, a configuration enabling moderation is still
 * refused, and the refusal comes from the build step rather than from the capability check —
 * which is exactly how it behaved before the method was added.
 */
public final class FakeLegacyProviderFactory implements ProviderFactory {

    @Override
    public String providerId() {
        return "fake-legacy";
    }

    @Override
    public TokenEstimation tokenEstimation() {
        return TokenEstimation.ABSENT;
    }

    // No supportsModeration(). That absence is the point of this class: removing it would
    // delete the only coverage the SPI's default has.

    @Override
    public void validate(LlmConfig config) {
        // As it was written: nothing this provider knows to reject.
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
        return Optional.empty();
    }

    @Override
    public Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config) {
        return Optional.empty();
    }
}
