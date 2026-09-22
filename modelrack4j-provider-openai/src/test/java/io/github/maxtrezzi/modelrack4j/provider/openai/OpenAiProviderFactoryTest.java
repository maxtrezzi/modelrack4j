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
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.langchain4j.model.ModelProvider;
import io.github.maxtrezzi.modelrack4j.ConfigValidationException;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import io.github.maxtrezzi.modelrack4j.spi.KeyRequirement;
import io.github.maxtrezzi.modelrack4j.spi.ProviderFactory;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Offline tests: configuration in, correctly parameterised model object out. No API key —
 * LangChain4j builders do not contact the provider, so building runs with a dummy
 * credential. The tests that check {@code base-url} and the missing key make calls, all to a
 * port on this machine.
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
    @DisplayName("permits both api-key and base-url, and requires neither")
    void declaresItsKeyRequirements() {
        assertThat(factory.apiKeyRequirement()).isEqualTo(KeyRequirement.OPTIONAL);
        assertThat(factory.baseUrlRequirement()).isEqualTo(KeyRequirement.OPTIONAL);
    }

    @Test
    @DisplayName("base-url reaches every model this factory builds, so none calls the vendor")
    void baseUrlReachesEveryModel() throws Exception {
        // Moderation included: a block behind a proxy must not send its moderation to
        // api.openai.com. The token count estimator is local and has no address.
        try (CountingPort port = new CountingPort()) {
            LlmConfig config = config("gpt-4o-mini", Optional.empty(),
                    Optional.of("test-key-not-used"), Optional.of(port.url()));

            port.assertReachedBy("the chat model",
                    () -> factory.createChatModel(config).chat("hi"));
            port.assertReachedBy("the streaming chat model", () -> CountingPort.streamOnce(
                    factory.createStreamingChatModel(config).orElseThrow()));
            port.assertReachedBy("the moderation model",
                    () -> factory.createModerationModel(config).orElseThrow().moderate("hi"));
        }
    }

    @Test
    @DisplayName("a block with neither api-key nor base-url is refused, and told both ways out")
    void neitherKeyNorAddressIsRefused() {
        assertThatThrownBy(() -> factory.validate(
                config("gpt-4o-mini", Optional.empty(), Optional.empty(), Optional.empty())))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageStartingWith("llm.CR has neither api-key nor base-url.")
                .hasMessageContaining("api.openai.com")
                .hasMessageContaining("Set api-key, or set base-url");
    }

    @Test
    @DisplayName("a block with a base-url and no api-key builds, for a server that needs no key")
    void anAddressWithoutAKeyBuilds() throws IOException {
        Path file = dir.resolve("local.conf");
        Files.writeString(file, """
                llm {
                  LOCAL {
                    provider   = openai
                    base-url   = "http://127.0.0.1:1/v1"
                    model-name = "llama3.2"
                    moderation { enabled = true }
                  }
                }
                """, StandardCharsets.UTF_8);

        try (var registry = LlmRegistry.builder().configFiles(List.of(file)).build()) {
            var bundle = registry.get("LOCAL");

            assertThat(bundle.config().apiKey()).isEmpty();
            assertThat(bundle.moderationModel()).isPresent();
        }
    }

    @Test
    @DisplayName("with no api-key the models send no Authorization header")
    void noKeyMeansNoAuthorizationHeader() throws Exception {
        // ADR-0062 rests on this: the client adds the header only when it has a key, so a
        // local server sees no placeholder credential. Read from the request itself.
        try (ServerSocket server =
                new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            LlmConfig config = config("gpt-4o-mini", Optional.empty(), Optional.empty(),
                    Optional.of("http://127.0.0.1:" + server.getLocalPort() + "/v1"));
            CompletableFuture<String> request = CompletableFuture.supplyAsync(() -> {
                // One request is all this reads. Closing the listener with it makes a retry
                // fail at once instead of waiting in the backlog for an answer that never
                // comes.
                try (server; Socket socket = server.accept()) {
                    return readHeaders(socket.getInputStream());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            catchThrowable(() -> factory.createChatModel(config).chat("hi"));

            assertThat(request.get(30, TimeUnit.SECONDS).toLowerCase(Locale.ROOT))
                    .contains("post /v1/chat/completions")
                    .doesNotContain("authorization:");
        }
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

    /** Reads a request up to the blank line that ends its headers. */
    private static String readHeaders(InputStream in) throws IOException {
        StringBuilder headers = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            headers.append((char) c);
            if (headers.length() >= 4
                    && headers.substring(headers.length() - 4).equals("\r\n\r\n")) {
                break;
            }
        }
        return headers.toString();
    }

    private static LlmConfig config(String modelName, Optional<Double> temperature) {
        return config(modelName, temperature, Optional.of("test-key-not-used"), Optional.empty());
    }

    private static LlmConfig config(String modelName, Optional<Double> temperature,
            Optional<String> apiKey, Optional<String> baseUrl) {
        return new LlmConfig("CR", Optional.empty(), "openai", apiKey, baseUrl, modelName,
                temperature, Duration.ofSeconds(60), false, false, false, Optional.empty(),
                false, "{}");
    }
}
