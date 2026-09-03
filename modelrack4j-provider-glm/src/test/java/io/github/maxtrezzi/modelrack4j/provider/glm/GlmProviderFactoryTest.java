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
package io.github.maxtrezzi.modelrack4j.provider.glm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;
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
 * Build-only tests. GLM is the narrowest provider in v1, and the two capabilities it does
 * <em>not</em> have are asserted here, because both change what configuration is legal.
 */
class GlmProviderFactoryTest {

    /** From the module's own {@code ChatCompletionModel} enum, not from recollection. */
    private static final String MODEL = "glm-4.6";

    /**
     * A key of the shape the provider requires: an id, a dot, and a secret of at least 16
     * bytes. Not a credential — it never leaves the process, because no test here calls out.
     */
    private static final String KEY = "e7c1a2b3.0123456789abcdef0123456789abcdef";

    @TempDir
    Path dir;

    private final GlmProviderFactory factory = new GlmProviderFactory();

    @Test
    @DisplayName("is discovered through ServiceLoader under its provider id")
    void isDiscoverable() {
        assertThat(ServiceLoader.load(ProviderFactory.class))
                .extracting(ProviderFactory::providerId)
                .contains("glm");
    }

    @Test
    @DisplayName("reports no token estimation at all, which is stricter than remote counting")
    void reportsNoTokenEstimation() {
        assertThat(factory.tokenEstimation()).isEqualTo(TokenEstimation.ABSENT);
    }

    @Test
    @DisplayName("the chat model carries the configured model name and temperature")
    void chatModelIsParameterised() {
        var model = factory.createChatModel(config(Optional.of(0.9), false));

        // The builder key is `model`, not `modelName` as in the other three modules; this
        // asserts the mapping from the schema's model-name actually arrived.
        assertThat(model).isInstanceOf(ZhipuAiChatModel.class);
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo(MODEL);
        assertThat(model.defaultRequestParameters().temperature()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("reports itself as OTHER, so provider() cannot identify a GLM model")
    void providerIsOther() {
        // Not a defect to fix here, but a fact worth pinning: an application routing on
        // ChatModel.provider() cannot tell GLM from any other community module.
        assertThat(factory.createChatModel(config(Optional.empty(), false)).provider())
                .isEqualTo(ModelProvider.OTHER);
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
                llm { SL { provider = glm, model-name = "glm-4.6"
                           api-key = "e7c1a2b3.0123456789abcdef0123456789abcdef"
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
    @DisplayName("a key with no '.' is rejected when the configuration loads, not on the "
            + "first call")
    void keyWithoutASecretIsRejectedAtLoad() {
        // Through the registry rather than through validate(), because the point of the
        // check is when it fires: building the registry is the last moment before an
        // application would hand the bundle to a caller.
        assertThatThrownBy(() -> registryFrom("""
                llm { SL { provider = glm, model-name = "glm-4.6"
                           api-key = "no-dot-in-here-at-all" } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("llm.SL.api-key")
                .hasMessageContaining("id.secret")
                .hasMessageContaining("no '.'");
    }

    @Test
    @DisplayName("a key ending in '.' is rejected too, because split() drops the empty tail")
    void keyEndingInADotIsRejected() {
        // Not the same case as a key with no dot at all, though it fails identically:
        // "id.".split("\\.") is one element, so there is no second part to reach for.
        assertThatThrownBy(() -> factory.validate(withKey("e7c1a2b3.")))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("no '.'");
    }

    @Test
    @DisplayName("a secret shorter than 16 bytes is rejected, and the message gives its size")
    void shortSecretIsRejected() {
        assertThatThrownBy(() -> factory.validate(withKey("e7c1a2b3.short")))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("secret part is 5 bytes")
                .hasMessageContaining("at least 16");
    }

    @Test
    @DisplayName("16 bytes is the boundary: 15 is refused, 16 passes")
    void sixteenBytesIsTheBoundary() {
        // Measured against the provider, not chosen: at 15 bytes its JWT library refuses to
        // sign, at 16 the request reaches the network. RFC 7518 section 3.2 fixes it at the
        // HS256 hash output size.
        assertThatThrownBy(() -> factory.validate(withKey("e7c1a2b3.0123456789abcde")))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("secret part is 15 bytes");

        factory.validate(withKey("e7c1a2b3.0123456789abcdef"));
    }

    @Test
    @DisplayName("the 16 are bytes, not characters, so a short non-ASCII secret still passes")
    void theLimitCountsBytesRatherThanCharacters() {
        // Eight two-byte characters: 16 bytes, and a check written on String.length() would
        // reject this key even though the provider signs with it happily.
        factory.validate(withKey("e7c1a2b3.\u00fb\u00fb\u00fb\u00fb\u00fb\u00fb\u00fb\u00fb"));
    }

    @Test
    @DisplayName("an empty id half is left to the provider, which answers it over the network")
    void anEmptyIdIsNotRejectedHere() {
        // Deliberate boundary. A key like ".<secret>" signs and sends fine, and the server
        // rejects it as a real ZhipuAiException. Refusing it here would be this library
        // guessing about credentials rather than about a shape the provider's code needs.
        factory.validate(withKey(".0123456789abcdef"));
    }

    @Test
    @DisplayName("the rejection message never contains the key")
    void theMessageDoesNotLeakTheKey() {
        // ADR-0047: the component holds the credential after substitution. A message quoting
        // the key would put it wherever the application logs its configuration errors.
        String secret = "0123456789abcde";
        assertThatThrownBy(() -> factory.validate(withKey("e7c1a2b3." + secret)))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageNotContaining(secret)
                .hasMessageNotContaining("e7c1a2b3");
    }

    @Test
    @DisplayName("streaming is available; moderation and token counting are not")
    void capabilitiesMatchTheMatrix() {
        LlmConfig config = config(Optional.of(0.2), false);

        assertThat(factory.createStreamingChatModel(config)).isPresent();
        assertThat(factory.createModerationModel(config)).isEmpty();
        assertThat(factory.createTokenCountEstimator(config)).isEmpty();
    }

    @Test
    @DisplayName("token-window memory cannot be opted into, and the message names the way out")
    void tokenWindowIsRefusedOutright() {
        // The opt-in flag from ADR-0027 covers a cost, not an absence. Setting it here must
        // not smuggle a configuration through that has no estimator to run.
        assertThatThrownBy(() -> registryFrom("""
                llm { SL { provider = glm, model-name = "glm-4.6"
                           api-key = "e7c1a2b3.0123456789abcdef0123456789abcdef"
                           memory { type = token-window, max-tokens = 500
                                    allow-remote-token-counting = true } } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("no token count estimator")
                .hasMessageContaining("message-window");
    }

    @Test
    @DisplayName("message-window memory, the documented alternative, does build")
    void messageWindowMemoryIsAvailable() throws IOException {
        try (var registry = registryFrom("""
                llm { SL { provider = glm, model-name = "glm-4.6"
                           api-key = "e7c1a2b3.0123456789abcdef0123456789abcdef"
                           memory { type = message-window, max-messages = 20 } } }
                """)) {
            assertThat(registry.get("SL").chatMemoryProvider()).isPresent();
        }
    }

    @Test
    @DisplayName("a registry builds a GLM bundle end to end, offline")
    void registryBuildsThroughServiceLoader() throws IOException {
        try (var registry = registryFrom("""
                llm {
                  SL {
                    provider    = glm
                    api-key     = "e7c1a2b3.0123456789abcdef0123456789abcdef"
                    model-name  = "glm-4.6"
                    temperature = 0.2
                    streaming   = true
                  }
                }
                """)) {
            var bundle = registry.get("SL");

            assertThat(bundle.chatModel()).isInstanceOf(ZhipuAiChatModel.class);
            assertThat(bundle.streamingChatModel()).isPresent();
            assertThat(bundle.moderationModel()).isEmpty();
            assertThat(bundle.chatMemoryProvider()).isEmpty();
        }
    }

    private LlmRegistry registryFrom(String hocon) throws IOException {
        Path file = Files.createTempFile(dir, "glm", ".conf");
        Files.writeString(file, hocon, StandardCharsets.UTF_8);
        return LlmRegistry.builder().configFiles(List.of(file)).build();
    }

    private static LlmConfig withKey(String apiKey) {
        return new LlmConfig("SL", Optional.empty(), "glm", apiKey, MODEL, Optional.empty(),
                Duration.ofSeconds(60), false, false, false, Optional.empty(), false);
    }

    private static LlmConfig config(Optional<Double> temperature, boolean moderation) {
        return new LlmConfig("SL", Optional.empty(), "glm", KEY, MODEL,
                temperature, Duration.ofSeconds(60), false, false, false, Optional.empty(), moderation);
    }
}
