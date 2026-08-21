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
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.Optional;

/**
 * A deliberately misbehaving factory: its {@code validate} accepts everything, but it then
 * produces nothing for the capabilities the configuration asked for.
 *
 * <p>This is the provider bug the registry has to catch — a factory that forgets to validate
 * would otherwise yield a bundle quietly missing what was configured.
 */
public final class FakeIncompleteProviderFactory extends FakeProviderFactory {

    @Override
    public String providerId() {
        return "fake-incomplete";
    }

    @Override
    public TokenEstimation tokenEstimation() {
        return TokenEstimation.LOCAL;
    }

    @Override
    protected boolean supportsModeration() {
        return true; // claims support, so validate() raises nothing
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config) {
        return Optional.empty();
    }

    @Override
    public Optional<ModerationModel> createModerationModel(LlmConfig config) {
        return Optional.empty();
    }
}
