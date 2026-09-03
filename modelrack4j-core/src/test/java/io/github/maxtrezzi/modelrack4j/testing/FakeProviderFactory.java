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

import dev.langchain4j.data.message.ChatMessage;
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
 * A provider factory that builds inert objects, so the core suite runs with no network and no
 * API keys. The concrete subclasses differ only in the capabilities they report, which is
 * what the capability rules are validated against.
 */
public abstract class FakeProviderFactory implements ProviderFactory {

    /** An inert chat model. Every method on the interface has a default, so this is empty. */
    public static final class FakeChatModel implements ChatModel {
    }

    /** An inert streaming chat model. */
    public static final class FakeStreamingChatModel implements StreamingChatModel {
    }

    /** An inert moderation model. */
    public static final class FakeModerationModel implements ModerationModel {
    }

    /** A local estimator that counts characters, so it needs no network. */
    public static final class FakeTokenCountEstimator implements TokenCountEstimator {

        @Override
        public int estimateTokenCountInText(String text) {
            return text == null ? 0 : text.length();
        }

        @Override
        public int estimateTokenCountInMessage(ChatMessage message) {
            return 1;
        }

        @Override
        public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
            int total = 0;
            for (ChatMessage ignored : messages) {
                total++;
            }
            return total;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Abstract rather than defaulted here, so every fake has to state its
     *     moderation capability and none of them inherits the SPI's compatibility default
     *     by accident.
     */
    @Override
    public abstract boolean supportsModeration();

    @Override
    public void validate(LlmConfig config) {
        // Nothing to reject: core applies the moderation rule from supportsModeration()
        // above, and these fakes exist to exercise exactly that path.
    }

    @Override
    public ChatModel createChatModel(LlmConfig config) {
        return new FakeChatModel();
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config) {
        return Optional.of(new FakeStreamingChatModel());
    }

    @Override
    public Optional<ModerationModel> createModerationModel(LlmConfig config) {
        return supportsModeration() ? Optional.of(new FakeModerationModel()) : Optional.empty();
    }

    @Override
    public Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config) {
        return tokenEstimation() == TokenEstimation.ABSENT
                ? Optional.empty()
                : Optional.of(new FakeTokenCountEstimator());
    }
}
