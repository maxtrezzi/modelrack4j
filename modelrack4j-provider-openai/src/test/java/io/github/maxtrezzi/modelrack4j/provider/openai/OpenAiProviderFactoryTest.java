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
package io.github.maxtrezzi.modelrack4j.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.ModelProvider;
import io.github.maxtrezzi.modelrack4j.ConfigValidationException;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Build-only tests: configuration in, correctly parameterised model object out. No network
 * and no API key — LangChain4j builders do not contact the provider, so everything here runs
 * offline with a dummy credential.
 */
class OpenAiProviderFactoryTest {

    @TempDir
    Path dir;

    private final OpenAiProviderFactory factory = new OpenAiProviderFactory();

    @Test
    @DisplayName("is discovered through ServiceLoader under its provider id")
    void isDiscoverable() {
        assertThat(ServiceLoader.load(ProviderFactory.class))
                .extracting(ProviderFactory::providerId)
                .contains("openai");
    }

    @Test
    @DisplayName("counts tokens locally, so token-window memory needs no opt-in")
    void countsTokensLocally() {
        assertThat(factory.tokenEstimation()).isEqualTo(TokenEstimation.LOCAL);
    }

    @Test
    @DisplayName("the chat model carries the configured model name and temperature")
    void chatModelIsParameterised() {
        var model = factory.createChatModel(config("gpt-4o-mini", Optional.of(0.3)));

        assertThat(model.provider()).isEqualTo(ModelProvider.OPEN_AI);
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo("gpt-4o-mini");
        assertThat(model.defaultRequestParameters().temperature()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("an omitted temperature is left to the provider rather than defaulted here")
    void omittedTemperatureIsNotInvented() {
        var model = factory.createChatModel(config("gpt-4o-mini", Optional.empty()));

        assertThat(model.defaultRequestParameters().temperature()).isNull();
    }

    @Test
    @DisplayName("streaming, moderation and a token estimator are all available")
    void everyCapabilityIsSupplied() {
        LlmConfig config = config("gpt-4o-mini", Optional.of(0.3));

        assertThat(factory.createStreamingChatModel(config)).isPresent();
        assertThat(factory.createModerationModel(config)).isPresent();
        assertThat(factory.createTokenCountEstimator(config)).isPresent();
    }

    @Test
    @DisplayName("a model with no local tokenizer fails naming the model, not deep inside memory")
    void unknownTokenizerModelIsReported() {
        // jtokkit only knows the encodings it shipped with. Reaching this is rare, but the
        // failure would otherwise surface during memory eviction rather than at build time.
        assertThatThrownBy(() ->
                factory.createTokenCountEstimator(config("no-such-model-xyz", Optional.empty())))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("no-such-model-xyz")
                .hasMessageContaining("message-window");
    }

    @Test
    @DisplayName("a registry builds an OpenAI bundle end to end, offline")
    void registryBuildsThroughServiceLoader() throws IOException {
        Path file = dir.resolve("openai.conf");
        Files.writeString(file, """
                llm {
                  CR {
                    provider    = openai
                    api-key     = "test-key-not-used"
                    model-name  = "gpt-4o-mini"
                    temperature = 0.7
                    streaming   = true
                    moderation { enabled = true }
                    memory { type = token-window, max-tokens = 500 }
                  }
                }
                """, StandardCharsets.UTF_8);

        try (var registry = LlmRegistry.builder().configFiles(List.of(file)).build()) {
            var bundle = registry.get("CR");

            assertThat(bundle.chatModel().provider()).isEqualTo(ModelProvider.OPEN_AI);
            assertThat(bundle.streamingChatModel()).isPresent();
            assertThat(bundle.moderationModel()).isPresent();
            // token-window on a LOCAL counter needs no allow-remote-token-counting flag.
            assertThat(bundle.chatMemoryProvider()).isPresent();
        }
    }

    private static LlmConfig config(String modelName, Optional<Double> temperature) {
        return new LlmConfig("CR", "openai", "test-key-not-used", modelName, temperature,
                Duration.ofSeconds(60), false, false, false, Optional.empty(), false);
    }
}
