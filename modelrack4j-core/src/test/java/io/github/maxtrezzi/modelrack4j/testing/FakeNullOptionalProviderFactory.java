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
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.moderation.ModerationModel;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.util.Optional;

/**
 * A factory that returns {@code null} from every method the SPI declares as returning
 * {@code Optional}.
 *
 * <p>{@link FakeNullProviderFactory} covers the required chat model, which fails before any
 * optional capability is reached; this one builds a real chat model so that each of the three
 * optional ones can be asked for in turn. All three must surface as a configuration error
 * naming the provider, not as a bare {@code NullPointerException} from inside core.
 */
public final class FakeNullOptionalProviderFactory extends FakeProviderFactory {

    @Override
    public String providerId() {
        return "fake-null-optional";
    }

    @Override
    public TokenEstimation tokenEstimation() {
        // LOCAL, so core's capability rules let token-window memory through and the null
        // below is what has to be caught.
        return TokenEstimation.LOCAL;
    }

    @Override
    public boolean supportsModeration() {
        return true;
    }

    @Override
    public Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config) {
        return null;
    }

    @Override
    public Optional<ModerationModel> createModerationModel(LlmConfig config) {
        return null;
    }

    @Override
    public Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config) {
        return null;
    }
}
