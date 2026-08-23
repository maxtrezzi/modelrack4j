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
package io.github.maxtrezzi.modelrack4j.provider.anthropic;

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
 * Build-only tests. The two facts that make Anthropic differ from OpenAI — no moderation
 * model, and remote token counting — are asserted here rather than trusted.
 */
class AnthropicProviderFactoryTest {

    @TempDir
    Path dir;

    private final AnthropicProviderFactory factory = new AnthropicProviderFactory();

    @Test
    @DisplayName("is discovered through ServiceLoader under its provider id")
    void isDiscoverable() {
        assertThat(ServiceLoader.load(ProviderFactory.class))
                .extracting(ProviderFactory::providerId)
                .contains("anthropic");
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

        assertThat(model.provider()).isEqualTo(ModelProvider.ANTHROPIC);
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo("claude-sonnet-4-5");
        assertThat(model.defaultRequestParameters().temperature()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("enabling moderation is rejected, and the message says where moderation lives")
    void moderationIsRejected() {
        assertThatThrownBy(() -> factory.validate(config(Optional.empty(), true)))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("no")
                .hasMessageContaining("moderation")
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
                llm { SL { provider = anthropic, api-key = "k", model-name = "claude-sonnet-4-5"
                           memory { type = token-window, max-tokens = 500 } } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("allow-remote-token-counting");

        try (var registry = registryFrom("""
                llm { SL { provider = anthropic, api-key = "k", model-name = "claude-sonnet-4-5"
                           memory { type = token-window, max-tokens = 500
                                    allow-remote-token-counting = true } } }
                """)) {
            assertThat(registry.get("SL").chatMemoryProvider()).isPresent();
        }
    }

    @Test
    @DisplayName("a registry builds an Anthropic bundle end to end, offline")
    void registryBuildsThroughServiceLoader() throws IOException {
        try (var registry = registryFrom("""
                llm {
                  SL {
                    provider    = anthropic
                    api-key     = "test-key-not-used"
                    model-name  = "claude-sonnet-4-5"
                    temperature = 0.2
                    streaming   = true
                    memory { type = message-window, max-messages = 20 }
                  }
                }
                """)) {
            var bundle = registry.get("SL");

            assertThat(bundle.chatModel().provider()).isEqualTo(ModelProvider.ANTHROPIC);
            assertThat(bundle.streamingChatModel()).isPresent();
            assertThat(bundle.moderationModel()).isEmpty();
            assertThat(bundle.chatMemoryProvider()).isPresent();
        }
    }

    private LlmRegistry registryFrom(String hocon) throws IOException {
        Path file = Files.createTempFile(dir, "anthropic", ".conf");
        Files.writeString(file, hocon, StandardCharsets.UTF_8);
        return LlmRegistry.builder().configFiles(List.of(file)).build();
    }

    private static LlmConfig config(Optional<Double> temperature, boolean moderation) {
        return new LlmConfig("SL", "anthropic", "test-key-not-used", "claude-sonnet-4-5",
                temperature, Duration.ofSeconds(60), false, false, false, Optional.empty(),
                moderation);
    }
}
