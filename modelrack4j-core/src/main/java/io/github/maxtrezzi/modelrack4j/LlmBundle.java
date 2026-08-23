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
package io.github.maxtrezzi.modelrack4j;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.moderation.ModerationModel;
import java.util.Objects;
import java.util.Optional;

/**
 * The set of LangChain4j objects built from one named configuration block.
 *
 * <p>A bundle is a value: it holds the objects and the configuration they came from, and
 * nothing else. It is never mutated — a reload produces a new bundle and swaps it into the
 * registry.
 *
 * <p><strong>Do not cache a bundle for the lifetime of your application.</strong> Call
 * {@link LlmRegistry#get(String)} each time you need one. A cached bundle keeps working, but
 * it is the configuration from whenever you fetched it, and it will never see a reload — the
 * classic trap with reloadable configuration.
 *
 * @param config the configuration this bundle was built from
 * @param chatModel the chat model, always present
 * @param streamingChatModel the streaming model, present when {@code streaming = true}
 * @param moderationModel the moderation model, present when moderation is enabled and the
 *     provider supports it
 * @param chatMemoryProvider the memory provider, present when a {@code memory} block was
 *     configured
 */
public record LlmBundle(
        LlmConfig config,
        ChatModel chatModel,
        Optional<StreamingChatModel> streamingChatModel,
        Optional<ModerationModel> moderationModel,
        Optional<ChatMemoryProvider> chatMemoryProvider) {

    /** @throws NullPointerException if any component is null */
    public LlmBundle {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(chatModel, "chatModel");
        Objects.requireNonNull(streamingChatModel, "streamingChatModel");
        Objects.requireNonNull(moderationModel, "moderationModel");
        Objects.requireNonNull(chatMemoryProvider, "chatMemoryProvider");
    }

    /**
     * Returns the name of the configuration this bundle was built from.
     *
     * @return the configuration name
     */
    public String name() {
        return config.name();
    }
}
