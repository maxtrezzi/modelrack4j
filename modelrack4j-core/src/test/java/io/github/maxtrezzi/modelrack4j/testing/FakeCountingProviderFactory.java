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

import dev.langchain4j.model.chat.ChatModel;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.spi.ModelContext;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts the chat models it builds, so a test can tell whether a step ran before a model was
 * built or after.
 *
 * <p>The count is static because {@code ServiceLoader} creates the instance the registry
 * uses, and a test cannot reach it. Only one test reads it, and it resets it first.
 */
public final class FakeCountingProviderFactory extends FakeProviderFactory {

    /** How many chat models this factory has built since the last reset. */
    public static final AtomicInteger CHAT_MODELS_BUILT = new AtomicInteger();

    @Override
    public String providerId() {
        return "fake-counting";
    }

    @Override
    public TokenEstimation tokenEstimation() {
        return TokenEstimation.LOCAL;
    }

    @Override
    public boolean supportsModeration() {
        return false;
    }

    @Override
    public ChatModel createChatModel(LlmConfig config, ModelContext context) {
        CHAT_MODELS_BUILT.incrementAndGet();
        return super.createChatModel(config, context);
    }
}
