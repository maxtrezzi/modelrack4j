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
 * A factory that breaks the SPI contract by returning {@code null} where a model is required.
 *
 * <p>The SPI is an extension point implemented in separate modules, so a factory that
 * misbehaves is a realistic failure rather than a hypothetical one. It must surface as a
 * configuration error naming the provider, not as a bare {@code NullPointerException}.
 */
public final class FakeNullProviderFactory extends FakeProviderFactory {

    @Override
    public String providerId() {
        return "fake-null";
    }

    @Override
    public TokenEstimation tokenEstimation() {
        return TokenEstimation.LOCAL;
    }

    @Override
    protected boolean supportsModeration() {
        return false;
    }

    @Override
    public ChatModel createChatModel(LlmConfig config) {
        return null;
    }
}
