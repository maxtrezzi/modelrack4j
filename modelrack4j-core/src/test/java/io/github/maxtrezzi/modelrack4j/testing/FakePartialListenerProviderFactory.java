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

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.moderation.ModerationModel;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.spi.ModelContext;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.Optional;

/**
 * Passes the listeners to its chat model and to nothing else.
 *
 * <p>The chat model is checked first, so a factory that drops every listener is refused
 * before the other two are built. This one gets past that check, so the checks on the
 * streaming and the moderation model are the ones that have to catch it.
 */
public final class FakePartialListenerProviderFactory extends FakeProviderFactory {

    @Override
    public String providerId() {
        return "fake-partial";
    }

    @Override
    public TokenEstimation tokenEstimation() {
        return TokenEstimation.LOCAL;
    }

    @Override
    public boolean supportsModeration() {
        return true;
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(
            LlmConfig config, ModelContext context) {
        return Optional.of(new FakeStreamingChatModel());
    }

    @Override
    public Optional<ModerationModel> createModerationModel(
            LlmConfig config, ModelContext context) {
        return Optional.of(new FakeModerationModel());
    }
}
