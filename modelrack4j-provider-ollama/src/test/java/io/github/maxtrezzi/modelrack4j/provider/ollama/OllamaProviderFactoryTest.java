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
package io.github.maxtrezzi.modelrack4j.provider.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.ModelProvider;
import io.github.maxtrezzi.modelrack4j.ConfigValidationException;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import io.github.maxtrezzi.modelrack4j.spi.KeyRequirement;
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
 * Offline tests. Ollama is the first provider that takes no key and needs an address, so
 * both requirements are asserted here, through the registry, as well as its two missing
 * capabilities. Most tests only build; the one that checks {@code base-url} makes calls, all
 * to a port on this machine.
 */
class OllamaProviderFactoryTest {

    private static final String MODEL = "llama3.2";

    /** Nothing listens here. Building a model does not connect, so this is never reached. */
    private static final String ADDRESS = "http://127.0.0.1:1";

    @TempDir
    Path dir;

    private final OllamaProviderFactory factory = new OllamaProviderFactory();

    @Test
    @DisplayName("is discovered through ServiceLoader under its provider id")
    void isDiscoverable() {
        assertThat(ServiceLoader.load(ProviderFactory.class))
                .extracting(ProviderFactory::providerId)
                .contains("ollama");
    }

    @Test
    @DisplayName("forbids api-key and requires base-url")
    void declaresItsKeyRequirements() {
        assertThat(factory.apiKeyRequirement()).isEqualTo(KeyRequirement.FORBIDDEN);
        assertThat(factory.baseUrlRequirement()).isEqualTo(KeyRequirement.MANDATORY);
    }

    @Test
    @DisplayName("reports no token estimation and no moderation")
    void reportsNeitherCapability() {
        assertThat(factory.tokenEstimation()).isEqualTo(TokenEstimation.ABSENT);
        assertThat(factory.supportsModeration()).isFalse();
    }

    @Test
    @DisplayName("the chat model carries the configured model name and temperature")
    void chatModelIsParameterised() {
        var model = factory.createChatModel(config(Optional.of(0.3), ADDRESS));

        assertThat(model.provider()).isEqualTo(ModelProvider.OLLAMA);
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo(MODEL);
        assertThat(model.defaultRequestParameters().temperature()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("an omitted temperature is left to the model rather than defaulted here")
    void omittedTemperatureIsNotInvented() {
        var model = factory.createChatModel(config(Optional.empty(), ADDRESS));

        assertThat(model.defaultRequestParameters().temperature()).isNull();
    }

    @Test
    @DisplayName("base-url reaches both models this factory builds")
    void baseUrlReachesEveryModel() throws Exception {
        try (CountingPort port = new CountingPort()) {
            LlmConfig config = config(Optional.empty(), port.url());

            port.assertReachedBy("the chat model",
                    () -> factory.createChatModel(config).chat("hi"));
            port.assertReachedBy("the streaming chat model", () -> CountingPort.streamOnce(
                    factory.createStreamingChatModel(config).orElseThrow()));
        }
    }

    @Test
    @DisplayName("a block with no base-url is refused, naming the key")
    void aMissingAddressIsRefused() {
        assertThatThrownBy(() -> registryFrom("""
                llm { LOCAL { provider = ollama, model-name = "llama3.2" } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessage("llm.LOCAL has no base-url, but provider 'ollama' requires one."
                        + " Set base-url in this block.");
    }

    @Test
    @DisplayName("a block that sets api-key is refused, because Ollama would ignore it")
    void aKeyIsRefused() {
        assertThatThrownBy(() -> registryFrom("""
                llm { LOCAL { provider = ollama, base-url = "http://127.0.0.1:1"
                              api-key = "not-used", model-name = "llama3.2" } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessage("llm.LOCAL sets api-key, but provider 'ollama' does not use one."
                        + " Remove api-key from this block.");
    }

    @Test
    @DisplayName("enabling moderation is refused through the registry")
    void moderationIsRejected() {
        assertThatThrownBy(() -> registryFrom("""
                llm { LOCAL { provider = ollama, base-url = "http://127.0.0.1:1"
                              model-name = "llama3.2", moderation { enabled = true } } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("ollama")
                .hasMessageContaining("ships no moderation model");
    }

    @Test
    @DisplayName("token-window memory cannot be opted into, and the message names the way out")
    void tokenWindowIsRefusedOutright() {
        assertThatThrownBy(() -> registryFrom("""
                llm { LOCAL { provider = ollama, base-url = "http://127.0.0.1:1"
                              model-name = "llama3.2"
                              memory { type = token-window, max-tokens = 500
                                       allow-remote-token-counting = true } } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("no token count estimator")
                .hasMessageContaining("message-window");
    }

    @Test
    @DisplayName("a registry builds an Ollama bundle end to end, offline")
    void registryBuildsThroughServiceLoader() throws IOException {
        try (LlmRegistry<?> registry = registryFrom("""
                llm {
                  LOCAL {
                    provider   = ollama
                    base-url   = "http://127.0.0.1:1"
                    model-name = "llama3.2"
                    streaming  = true
                    memory { type = message-window, max-messages = 20 }
                  }
                }
                """)) {
            var bundle = registry.get("LOCAL");

            assertThat(bundle.chatModel().provider()).isEqualTo(ModelProvider.OLLAMA);
            assertThat(bundle.streamingChatModel()).isPresent();
            assertThat(bundle.moderationModel()).isEmpty();
            assertThat(bundle.chatMemoryProvider()).isPresent();
        }
    }

    private LlmRegistry<?> registryFrom(String hocon) throws IOException {
        Path file = Files.createTempFile(dir, "ollama", ".conf");
        Files.writeString(file, hocon, StandardCharsets.UTF_8);
        return LlmRegistry.builder().configFiles(List.of(file)).build();
    }

    private static LlmConfig config(Optional<Double> temperature, String baseUrl) {
        return new LlmConfig("LOCAL", Optional.empty(), "ollama", Optional.empty(),
                Optional.of(baseUrl), MODEL, temperature, Duration.ofSeconds(60), false, false,
                false, Optional.empty(), false, "{}");
    }
}
