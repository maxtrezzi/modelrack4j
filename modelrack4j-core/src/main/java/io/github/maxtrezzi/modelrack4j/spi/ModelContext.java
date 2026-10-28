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
package io.github.maxtrezzi.modelrack4j.spi;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import java.util.List;
import java.util.Objects;

/**
 * What the registry gives a factory besides the configuration: the objects the application
 * registered in code, which cannot come from a configuration file.
 *
 * <p>One context serves one configuration block. The registry builds it each time it builds
 * that block's bundle, and passes the same context to the chat, streaming and moderation
 * models of the bundle.
 *
 * <p>A factory passes every listener here to the builder of the model it creates. Core
 * checks the result: a model whose {@code listeners()} does not report them is refused, so
 * a listener the application registered is never dropped without a message.
 *
 * @implNote A final class with a static factory rather than a record, on purpose (ADR-0063).
 *     The context exists so that the next thing a factory needs from the registry is a new
 *     method here instead of another change to {@link ProviderFactory}. A record's canonical
 *     constructor would make every such addition a break for whoever constructs one, which
 *     includes every provider's own tests.
 */
public final class ModelContext {

    private static final ModelContext EMPTY = new ModelContext(List.of(), List.of());

    private final List<ChatModelListener> chatModelListeners;
    private final List<ModerationModelListener> moderationModelListeners;

    private ModelContext(List<? extends ChatModelListener> chatModelListeners,
            List<? extends ModerationModelListener> moderationModelListeners) {
        this.chatModelListeners = List.copyOf(chatModelListeners);
        this.moderationModelListeners = List.copyOf(moderationModelListeners);
    }

    /**
     * Returns a context with nothing in it, which is what a bundle gets when the application
     * registered no listeners.
     *
     * @return the empty context
     */
    public static ModelContext empty() {
        return EMPTY;
    }

    /**
     * Returns a context holding the given listeners.
     *
     * @param chatModelListeners the listeners for the chat and the streaming chat model, in
     *     the order LangChain4j should call them
     * @param moderationModelListeners the listeners for the moderation model, in order
     * @return the context, which holds copies of both lists
     * @throws NullPointerException if either list, or any element, is null
     */
    public static ModelContext of(List<? extends ChatModelListener> chatModelListeners,
            List<? extends ModerationModelListener> moderationModelListeners) {
        return new ModelContext(
                Objects.requireNonNull(chatModelListeners, "chatModelListeners"),
                Objects.requireNonNull(moderationModelListeners, "moderationModelListeners"));
    }

    /**
     * Returns the listeners for the chat model and the streaming chat model. LangChain4j uses
     * one listener type for both.
     *
     * @return the listeners, unmodifiable and possibly empty, never null
     */
    public List<ChatModelListener> chatModelListeners() {
        return chatModelListeners;
    }

    /**
     * Returns the listeners for the moderation model. LangChain4j gives moderation a listener
     * type of its own.
     *
     * @return the listeners, unmodifiable and possibly empty, never null
     */
    public List<ModerationModelListener> moderationModelListeners() {
        return moderationModelListeners;
    }
}
