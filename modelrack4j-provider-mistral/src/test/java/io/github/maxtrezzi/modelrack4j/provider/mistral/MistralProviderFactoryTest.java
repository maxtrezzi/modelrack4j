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
package io.github.maxtrezzi.modelrack4j.provider.mistral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.mistralai.MistralAiModerationModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.moderation.listener.ModerationModelRequestContext;
import io.github.maxtrezzi.modelrack4j.ConfigValidationException;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import io.github.maxtrezzi.modelrack4j.spi.KeyRequirement;
import io.github.maxtrezzi.modelrack4j.spi.ModelContext;
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
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Offline tests. Mistral moderates and has no token counter, which is the opposite pair to
 * Anthropic and Gemini, and its moderation model needs a model name of its own. Most tests
 * only build; the ones that check {@code base-url} and the listeners make calls, all to a
 * port on this machine.
 */
class MistralProviderFactoryTest {

    @TempDir
    Path dir;

    private final MistralProviderFactory factory = new MistralProviderFactory();

    @Test
    @DisplayName("is discovered through ServiceLoader under its provider id")
    void isDiscoverable() {
        assertThat(ServiceLoader.load(ProviderFactory.class))
                .extracting(ProviderFactory::providerId)
                .contains("mistral");
    }

    @Test
    @DisplayName("has no token counter, and can moderate")
    void reportsItsCapabilities() {
        assertThat(factory.tokenEstimation()).isEqualTo(TokenEstimation.ABSENT);
        assertThat(factory.supportsModeration()).isTrue();
    }

    @Test
    @DisplayName("requires api-key and permits base-url")
    void declaresItsKeyRequirements() {
        assertThat(factory.apiKeyRequirement()).isEqualTo(KeyRequirement.MANDATORY);
        assertThat(factory.baseUrlRequirement()).isEqualTo(KeyRequirement.OPTIONAL);
    }

    @Test
    @DisplayName("base-url reaches every model this factory builds, so none calls the vendor")
    void baseUrlReachesEveryModel() throws Exception {
        // Moderation included: a block behind a proxy must not send its moderation to
        // api.mistral.ai (ADR-0062).
        try (CountingPort port = new CountingPort()) {
            LlmConfig config = config(Optional.empty(), Optional.of(port.url()));

            port.assertReachedBy("the chat model",
                    () -> factory.createChatModel(config).chat("hi"));
            port.assertReachedBy("the streaming chat model", () -> CountingPort.streamOnce(
                    factory.createStreamingChatModel(config).orElseThrow()));
            port.assertReachedBy("the moderation model",
                    () -> factory.createModerationModel(config).orElseThrow().moderate("hi"));
        }
    }

    @Test
    @DisplayName("listeners from the context reach every model this factory builds")
    void listenersReachEveryModel() throws Exception {
        // ADR-0063. Against the local port, so each call is real and fails there: a listener
        // sees the request before it is sent, whatever happens to it afterwards.
        List<String> seen = new CopyOnWriteArrayList<>();
        ChatModelListener listener = new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext context) {
                seen.add("request");
            }
        };
        ModerationModelListener moderationListener = new ModerationModelListener() {
            @Override
            public void onRequest(ModerationModelRequestContext context) {
                seen.add("moderation request");
            }
        };
        ModelContext context = ModelContext.of(List.of(listener), List.of(moderationListener));

        try (CountingPort port = new CountingPort()) {
            LlmConfig config = config(Optional.empty(), Optional.of(port.url()));
            ChatModel chat = factory.createChatModel(config, context);
            StreamingChatModel streaming =
                    factory.createStreamingChatModel(config, context).orElseThrow();
            ModerationModel moderation =
                    factory.createModerationModel(config, context).orElseThrow();

            assertThat(chat.listeners()).containsExactly(listener);
            assertThat(streaming.listeners()).containsExactly(listener);
            assertThat(moderation.listeners()).containsExactly(moderationListener);
            assertThat(factory.createChatModel(config).listeners()).isEmpty();
            port.assertReachedBy("the chat model", () -> chat.chat("hi"));
            port.assertReachedBy("the streaming chat model",
                    () -> CountingPort.streamOnce(streaming));
            port.assertReachedBy("the moderation model", () -> moderation.moderate("hi"));
        }

        assertThat(seen).containsExactly("request", "request", "moderation request");
    }

    @Test
    @DisplayName("the moderation model is called with a moderation model, not the chat model")
    void moderationUsesItsOwnModelName() {
        // MistralAiModerationModel refuses a blank model name when it is built, and the
        // block's model-name is a chat model, which the moderation endpoint does not take.
        var moderation = (MistralAiModerationModel)
                factory.createModerationModel(config(Optional.empty())).orElseThrow();

        assertThat(moderation.modelName()).isEqualTo("mistral-moderation-latest");
    }

    @Test
    @DisplayName("a block with no api-key is refused through the registry, naming the key")
    void aMissingKeyIsRefused() {
        assertThatThrownBy(() -> registryFrom("""
                llm { CR { provider = mistral, model-name = "m" } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("has no api-key, but provider 'mistral' requires one");
    }

    @Test
    @DisplayName("token-window memory is refused, with message-window as the way out")
    void tokenWindowIsRefused() {
        assertThatThrownBy(() -> registryFrom("""
                llm { CR { provider = mistral, api-key = "k", model-name = "mistral-small-latest"
                           memory { type = token-window, max-tokens = 500 } } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("ships no token count estimator")
                .hasMessageContaining("message-window");
    }

    @Test
    @DisplayName("the chat model carries the configured model name and temperature")
    void chatModelIsParameterised() {
        var model = factory.createChatModel(config(Optional.of(0.4)));

        assertThat(model.provider()).isEqualTo(ModelProvider.MISTRAL_AI);
        assertThat(model.defaultRequestParameters().modelName())
                .isEqualTo("mistral-small-latest");
        assertThat(model.defaultRequestParameters().temperature()).isEqualTo(0.4);
    }

    @Test
    @DisplayName("an omitted temperature is left to the provider rather than defaulted here")
    void omittedTemperatureIsNotInvented() {
        var model = factory.createChatModel(config(Optional.empty()));

        assertThat(model.defaultRequestParameters().temperature()).isNull();
    }

    @Test
    @DisplayName("streaming and moderation are available; a token estimator is not")
    void capabilitiesMatchTheMatrix() {
        LlmConfig config = config(Optional.empty());

        assertThat(factory.createStreamingChatModel(config)).isPresent();
        assertThat(factory.createModerationModel(config)).isPresent();
        assertThat(factory.createTokenCountEstimator(config)).isEmpty();
    }

    @Test
    @DisplayName("a registry builds a Mistral bundle end to end, offline")
    void registryBuildsThroughServiceLoader() throws IOException {
        try (var registry = registryFrom("""
                llm {
                  CR {
                    provider    = mistral
                    api-key     = "test-key-not-used"
                    model-name  = "mistral-small-latest"
                    temperature = 0.2
                    streaming   = true
                    moderation { enabled = true }
                    memory { type = message-window, max-messages = 20 }
                  }
                }
                """)) {
            var bundle = registry.get("CR");

            assertThat(bundle.chatModel().provider()).isEqualTo(ModelProvider.MISTRAL_AI);
            assertThat(bundle.streamingChatModel()).isPresent();
            assertThat(bundle.moderationModel()).isPresent();
            assertThat(bundle.chatMemoryProvider()).isPresent();
        }
    }

    private LlmRegistry<?> registryFrom(String hocon) throws IOException {
        Path file = Files.createTempFile(dir, "mistral", ".conf");
        Files.writeString(file, hocon, StandardCharsets.UTF_8);
        return LlmRegistry.builder().configFiles(List.of(file)).build();
    }

    private static LlmConfig config(Optional<Double> temperature) {
        return config(temperature, Optional.empty());
    }

    private static LlmConfig config(Optional<Double> temperature, Optional<String> baseUrl) {
        return new LlmConfig("CR", Optional.empty(), "mistral", Optional.of("test-key-not-used"),
                baseUrl, "mistral-small-latest", temperature, Duration.ofSeconds(60), false,
                false, false, Optional.empty(), true, "{}");
    }
}
