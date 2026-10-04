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
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;

/**
 * Behaves like a provider jar built against another version of LangChain4j: building its chat
 * model calls a method that is not there.
 *
 * <p>The JVM reports that as {@code NoSuchMethodError}, a {@code LinkageError}; this factory
 * throws it itself, since a real mismatch cannot be compiled here.
 */
public final class FakeMismatchedProviderFactory extends FakeProviderFactory {

    @Override
    public String providerId() {
        return "fake-mismatched";
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
    public ChatModel createChatModel(LlmConfig config) {
        throw new NoSuchMethodError("'dev.langchain4j.Builder dev.langchain4j.Builder.gone()'");
    }
}
