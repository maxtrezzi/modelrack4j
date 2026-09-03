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
package io.github.maxtrezzi.modelrack4j.provider.gemini;

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
 * Build-only tests. Gemini's capabilities match Anthropic's rather than OpenAI's — no
 * moderation, remote token counting — and both are asserted here rather than trusted.
 */
class GeminiProviderFactoryTest {

    private static final String MODEL = "gemini-2.5-flash";

    @TempDir
    Path dir;

    private final GeminiProviderFactory factory = new GeminiProviderFactory();

    @Test
    @DisplayName("is discovered through ServiceLoader under its provider id")
    void isDiscoverable() {
        assertThat(ServiceLoader.load(ProviderFactory.class))
                .extracting(ProviderFactory::providerId)
                .contains("gemini");
    }

    @Test
    @DisplayName("counts tokens over the network, which is what makes the opt-in rule apply")
    void countsTokensRemotely() {
        assertThat(factory.tokenEstimation()).isEqualTo(TokenEstimation.REMOTE);
    }

    @Test
    @DisplayName("the chat model carries the configured model name and temperature")
    void chatModelIsParameterised() {
        var model = factory.createChatModel(config(Optional.of(0.9), false));

        assertThat(model.provider()).isEqualTo(ModelProvider.GOOGLE_AI_GEMINI);
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo(MODEL);
        assertThat(model.defaultRequestParameters().temperature()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("the factory reports that it cannot moderate")
    void moderationIsNotSupported() {
        // The module owns the capability; core owns the rule and the message. Asserting it
        // here rather than on validate() is what keeps the two from drifting apart.
        assertThat(factory.supportsModeration()).isFalse();
    }

    @Test
    @DisplayName("enabling moderation is refused through the registry, and the message says "
            + "where moderation lives")
    void moderationIsRejected() {
        assertThatThrownBy(() -> registryFrom("""
                llm { SL { provider = gemini, api-key = "k", model-name = "gemini-2.5-flash"
                           moderation { enabled = true } } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("ships no moderation model")
                .hasMessageContaining("OpenAI");
    }

    @Test
    @DisplayName("a configuration without moderation passes validation")
    void ordinaryConfigurationValidates() {
        factory.validate(config(Optional.of(0.2), false));
    }

    @Test
    @DisplayName("streaming and a token estimator are available; moderation is not")
    void capabilitiesMatchTheMatrix() {
        LlmConfig config = config(Optional.of(0.2), false);

        assertThat(factory.createStreamingChatModel(config)).isPresent();
        assertThat(factory.createTokenCountEstimator(config)).isPresent();
        assertThat(factory.createModerationModel(config)).isEmpty();
    }

    @Test
    @DisplayName("token-window memory is refused through the registry until the config opts in")
    void tokenWindowNeedsTheOptIn() throws IOException {
        assertThatThrownBy(() -> registryFrom("""
                llm { SL { provider = gemini, api-key = "k", model-name = "gemini-2.5-flash"
                           memory { type = token-window, max-tokens = 500 } } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("allow-remote-token-counting");

        try (var registry = registryFrom("""
                llm { SL { provider = gemini, api-key = "k", model-name = "gemini-2.5-flash"
                           memory { type = token-window, max-tokens = 500
                                    allow-remote-token-counting = true } } }
                """)) {
            assertThat(registry.get("SL").chatMemoryProvider()).isPresent();
        }
    }

    @Test
    @DisplayName("a registry builds a Gemini bundle end to end, offline")
    void registryBuildsThroughServiceLoader() throws IOException {
        try (var registry = registryFrom("""
                llm {
                  SL {
                    provider    = gemini
                    api-key     = "test-key-not-used"
                    model-name  = "gemini-2.5-flash"
                    temperature = 0.2
                    streaming   = true
                    memory { type = message-window, max-messages = 20 }
                  }
                }
                """)) {
            var bundle = registry.get("SL");

            assertThat(bundle.chatModel().provider()).isEqualTo(ModelProvider.GOOGLE_AI_GEMINI);
            assertThat(bundle.streamingChatModel()).isPresent();
            assertThat(bundle.moderationModel()).isEmpty();
            assertThat(bundle.chatMemoryProvider()).isPresent();
        }
    }

    private LlmRegistry registryFrom(String hocon) throws IOException {
        Path file = Files.createTempFile(dir, "gemini", ".conf");
        Files.writeString(file, hocon, StandardCharsets.UTF_8);
        return LlmRegistry.builder().configFiles(List.of(file)).build();
    }

    private static LlmConfig config(Optional<Double> temperature, boolean moderation) {
        return new LlmConfig("SL", Optional.empty(), "gemini", "test-key-not-used", MODEL,
                temperature, Duration.ofSeconds(60), false, false, false, Optional.empty(), moderation);
    }
}
