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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import io.github.maxtrezzi.modelrack4j.ConfigValidationException;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.spi.KeyRequirement;
import io.github.maxtrezzi.modelrack4j.spi.ModelContext;
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.List;
import java.util.Optional;

/**
 * A provider factory that builds inert objects, so the core suite runs with no network and no
 * API keys. The concrete subclasses differ only in the capabilities they report, which is
 * what the capability rules are validated against.
 *
 * <p>Both key requirements are constructor parameters rather than overridden methods, so a
 * fake for any of the three values of either key is one line, and core's rules can be tested
 * without a real provider (ADR-0062). The no-argument constructor makes both
 * {@link KeyRequirement#OPTIONAL}, which accepts every block the suite already writes.
 */
public abstract class FakeProviderFactory implements ProviderFactory {

    private final KeyRequirement apiKeyRequirement;
    private final KeyRequirement baseUrlRequirement;

    /** A fake that permits both keys and requires neither. */
    protected FakeProviderFactory() {
        this(KeyRequirement.OPTIONAL, KeyRequirement.OPTIONAL);
    }

    /**
     * A fake that reports the given requirements.
     *
     * @param apiKeyRequirement what {@link #apiKeyRequirement()} returns; null is allowed, to
     *     test that core refuses it
     * @param baseUrlRequirement what {@link #baseUrlRequirement()} returns; null is allowed
     */
    protected FakeProviderFactory(
            KeyRequirement apiKeyRequirement, KeyRequirement baseUrlRequirement) {
        this.apiKeyRequirement = apiKeyRequirement;
        this.baseUrlRequirement = baseUrlRequirement;
    }

    /**
     * A chat model that answers {@code "ok"} without a network, so a test can make a call and
     * watch the listeners LangChain4j runs around it.
     */
    public static final class FakeChatModel implements ChatModel {

        private final List<ChatModelListener> listeners;

        /** A model with no listeners, for the fakes that do not take a context. */
        public FakeChatModel() {
            this(List.of());
        }

        /**
         * A model reporting the given listeners.
         *
         * @param listeners what {@link #listeners()} returns, and LangChain4j calls
         */
        public FakeChatModel(List<ChatModelListener> listeners) {
            this.listeners = List.copyOf(listeners);
        }

        @Override
        public List<ChatModelListener> listeners() {
            return listeners;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }
    }

    /** A streaming chat model that completes at once with {@code "ok"}. */
    public static final class FakeStreamingChatModel implements StreamingChatModel {

        private final List<ChatModelListener> listeners;

        /** A model with no listeners. */
        public FakeStreamingChatModel() {
            this(List.of());
        }

        /**
         * A model reporting the given listeners.
         *
         * @param listeners what {@link #listeners()} returns, and LangChain4j calls
         */
        public FakeStreamingChatModel(List<ChatModelListener> listeners) {
            this.listeners = List.copyOf(listeners);
        }

        @Override
        public List<ChatModelListener> listeners() {
            return listeners;
        }

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            handler.onCompleteResponse(
                    ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());
        }
    }

    /** A moderation model that flags nothing. */
    public static final class FakeModerationModel implements ModerationModel {

        private final List<ModerationModelListener> listeners;

        /** A model with no listeners. */
        public FakeModerationModel() {
            this(List.of());
        }

        /**
         * A model reporting the given listeners.
         *
         * @param listeners what {@link #listeners()} returns, and LangChain4j calls
         */
        public FakeModerationModel(List<ModerationModelListener> listeners) {
            this.listeners = List.copyOf(listeners);
        }

        @Override
        public List<ModerationModelListener> listeners() {
            return listeners;
        }

        @Override
        public ModerationResponse doModerate(ModerationRequest request) {
            return ModerationResponse.builder().moderation(Moderation.notFlagged()).build();
        }
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
    public final KeyRequirement apiKeyRequirement() {
        return apiKeyRequirement;
    }

    @Override
    public final KeyRequirement baseUrlRequirement() {
        return baseUrlRequirement;
    }

    @Override
    public void validate(LlmConfig config) {
        // Nothing to reject: core applies the moderation rule from supportsModeration()
        // above, and these fakes exist to exercise exactly that path.
    }

    // The one-argument forms delegate, as the SPI asks of a factory written for 0.4.0. A fake
    // that misbehaves overrides the two-argument form, because that is the one core calls.

    @Override
    public final ChatModel createChatModel(LlmConfig config) {
        return createChatModel(config, ModelContext.empty());
    }

    @Override
    public ChatModel createChatModel(LlmConfig config, ModelContext context) {
        return new FakeChatModel(context.chatModelListeners());
    }

    @Override
    public final Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config) {
        return createStreamingChatModel(config, ModelContext.empty());
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(
            LlmConfig config, ModelContext context) {
        return Optional.of(new FakeStreamingChatModel(context.chatModelListeners()));
    }

    @Override
    public final Optional<ModerationModel> createModerationModel(LlmConfig config) {
        return createModerationModel(config, ModelContext.empty());
    }

    @Override
    public Optional<ModerationModel> createModerationModel(
            LlmConfig config, ModelContext context) {
        return supportsModeration()
                ? Optional.of(new FakeModerationModel(context.moderationModelListeners()))
                : Optional.empty();
    }

    @Override
    public Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config) {
        return tokenEstimation() == TokenEstimation.ABSENT
                ? Optional.empty()
                : Optional.of(new FakeTokenCountEstimator());
    }
}
