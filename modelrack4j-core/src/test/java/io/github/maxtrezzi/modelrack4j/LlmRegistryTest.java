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
package io.github.maxtrezzi.modelrack4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Building the registry: defaults, lookup, provider discovery and the capability rules. */
class LlmRegistryTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("omitted keys take the documented defaults")
    void defaultsAreApplied() throws IOException {
        var registry = registryOf("""
                llm { SL { provider = fake-local, api-key = "k", model-name = "m" } }
                """);
        LlmConfig config = registry.get("SL").config();

        assertThat(config.streaming()).isFalse();
        assertThat(config.logRequests()).isFalse();
        assertThat(config.logResponses()).isFalse();
        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.temperature()).isEmpty();
        assertThat(config.memory()).isEmpty();
        assertThat(config.moderationEnabled()).isFalse();
    }

    @Test
    @DisplayName("an unknown name throws and names what was asked for")
    void unknownNameThrows() throws IOException {
        var registry = registryOf("""
                llm { SL { provider = fake-local, api-key = "k", model-name = "m" } }
                """);

        assertThatThrownBy(() -> registry.get("NOPE"))
                .isInstanceOf(UnknownConfigurationException.class)
                .hasMessageContaining("NOPE");
        assertThat(registry.names()).containsExactly("SL");
    }

    @Test
    @DisplayName("every configured name is built")
    void allNamesAreBuilt() throws IOException {
        var registry = registryOf("""
                llm {
                  SL { provider = fake-local,  api-key = "k", model-name = "m" }
                  SH { provider = fake-remote, api-key = "k", model-name = "m" }
                  CR { provider = fake-absent, api-key = "k", model-name = "m" }
                }
                """);

        assertThat(registry.names()).containsExactly("CR", "SH", "SL");
    }

    @Test
    @DisplayName("streaming is built only when asked for")
    void streamingIsOptional() throws IOException {
        var registry = registryOf("""
                llm {
                  ON  { provider = fake-local, api-key = "k", model-name = "m", streaming = true }
                  OFF { provider = fake-local, api-key = "k", model-name = "m" }
                }
                """);

        assertThat(registry.get("ON").streamingChatModel()).isPresent();
        assertThat(registry.get("OFF").streamingChatModel()).isEmpty();
        assertThat(registry.get("OFF").chatModel()).isNotNull();
    }

    @Test
    @DisplayName("an unknown provider lists the providers actually on the classpath")
    void unknownProviderListsAvailable() {
        assertThatThrownBy(() -> registryOf("""
                llm { SL { provider = nope, api-key = "k", model-name = "m" } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("'nope'")
                .hasMessageContaining("fake-absent")
                .hasMessageContaining("fake-local")
                .hasMessageContaining("fake-remote")
                .hasMessageContaining("fake-incomplete");
    }

    @Test
    @DisplayName("a provider without moderation rejects a config that enables it")
    void moderationRejectedByProvider() {
        assertThatThrownBy(() -> registryOf("""
                llm { SL { provider = fake-remote, api-key = "k", model-name = "m"
                           moderation { enabled = true } } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("moderation");
    }

    @Test
    @DisplayName("nothing is built when any single block is invalid")
    void buildIsAllOrNothing() {
        assertThatThrownBy(() -> registryOf("""
                llm {
                  GOOD { provider = fake-local, api-key = "k", model-name = "m" }
                  BAD  { provider = fake-local, api-key = "k" }
                }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("BAD");
    }

    /** The rules from ADR-0027, which is where the token-window policy was settled. */
    @Nested
    @DisplayName("token-window memory")
    class TokenWindowRules {

        @Test
        @DisplayName("is built without ceremony when the provider counts locally")
        void localNeedsNoOptIn() throws IOException {
            var registry = registryOf("""
                    llm { SL { provider = fake-local, api-key = "k", model-name = "m"
                               memory { type = token-window, max-tokens = 500 } } }
                    """);

            assertThat(registry.get("SL").chatMemoryProvider()).isPresent();
        }

        @Test
        @DisplayName("is rejected on a remote counter, and the message names the flag that permits it")
        void remoteWithoutOptInIsRejectedAndSignpostsTheFlag() {
            // ADR-0027: the message is part of the contract. A shorter message that omits the
            // flag turns opt-in into outright rejection, because nothing else documents the
            // escape hatch at the point of failure.
            assertThatThrownBy(() -> registryOf("""
                    llm { SL { provider = fake-remote, api-key = "k", model-name = "m"
                               memory { type = token-window, max-tokens = 500 } } }
                    """))
                    .isInstanceOf(ConfigValidationException.class)
                    .hasMessageContaining("allow-remote-token-counting")
                    .hasMessageContaining("billed")
                    .hasMessageContaining("fake-remote");
        }

        @Test
        @DisplayName("is built on a remote counter once the configuration opts in")
        void remoteWithOptInIsBuilt() throws IOException {
            var registry = registryOf("""
                    llm { SL { provider = fake-remote, api-key = "k", model-name = "m"
                               memory { type = token-window, max-tokens = 500
                                        allow-remote-token-counting = true } } }
                    """);

            assertThat(registry.get("SL").chatMemoryProvider()).isPresent();
        }

        @Test
        @DisplayName("cannot be opted into when the provider has no estimator at all")
        void absentIsNotEscapable() {
            assertThatThrownBy(() -> registryOf("""
                    llm { SL { provider = fake-absent, api-key = "k", model-name = "m"
                               memory { type = token-window, max-tokens = 500
                                        allow-remote-token-counting = true } } }
                    """))
                    .isInstanceOf(ConfigValidationException.class)
                    .hasMessageContaining("no token count estimator")
                    .hasMessageContaining("message-window");
        }

        @Test
        @DisplayName("the opt-in flag is inert rather than an error on a local counter")
        void flagIsInertOnLocalProviders() throws IOException {
            // One config layer commonly spans several providers; making the key
            // provider-conditional would force users to split layers to satisfy a validator.
            var registry = registryOf("""
                    llm { SL { provider = fake-local, api-key = "k", model-name = "m"
                               memory { type = token-window, max-tokens = 500
                                        allow-remote-token-counting = true } } }
                    """);

            assertThat(registry.get("SL").chatMemoryProvider()).isPresent();
        }
    }

    @Test
    @DisplayName("message-window memory works on every provider")
    void messageWindowNeedsNoCapability() throws IOException {
        var registry = registryOf("""
                llm { SL { provider = fake-absent, api-key = "k", model-name = "m"
                           memory { type = message-window, max-messages = 10 } } }
                """);

        assertThat(registry.get("SL").chatMemoryProvider()).isPresent();
    }

    @Test
    @DisplayName("a requested capability the provider does not produce fails instead of vanishing")
    void requestedCapabilityMustBeProduced() {
        assertThatThrownBy(() -> registryOf("llm { SL { provider = fake-incomplete"
                + ", api-key = \"k\", model-name = \"m\", streaming = true } }"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("streaming = true")
                .hasMessageContaining("produced no streaming chat model");

        assertThatThrownBy(() -> registryOf("llm { SL { provider = fake-incomplete"
                + ", api-key = \"k\", model-name = \"m\", moderation { enabled = true } } }"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("produced no moderation model");
    }

    @Test
    @DisplayName("a factory returning null reports a config error, not a bare NullPointerException")
    void factoryReturningNullIsReportedAsAConfigError() {
        assertThatThrownBy(() -> registryOf(
                "llm { SL { provider = fake-null, api-key = \"k\", model-name = \"m\" } }"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("llm.SL")
                .hasMessageContaining("fake-null")
                .hasMessageContaining("produced no chat model");
    }

    @Test
    @DisplayName("a named block that is not an object fails as a config error, not a parser error")
    void nonObjectBlockIsRejected() {
        assertThatThrownBy(() -> registryOf("llm { SL = 5 }"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("llm.SL")
                .hasMessageContaining("NUMBER");
    }

    @Test
    @DisplayName("a name containing a quote is handled, not built into a broken path expression")
    void awkwardNamesAreHandled() throws IOException {
        // Concatenated rather than a text block: the point of the test is the exact escaping.
        String hocon = "llm {\n"
                + "  \"we\\\"ird\" { provider = fake-local, api-key = \"k\", model-name = \"m\" }\n"
                + "  \"SL.EU\"     { provider = fake-local, api-key = \"k\", model-name = \"m\" }\n"
                + "}\n";

        var registry = registryOf(hocon);

        assertThat(registry.names()).containsExactly("SL.EU", "we\"ird");
        assertThat(registry.get("we\"ird").chatModel()).isNotNull();
    }

    @Test
    @DisplayName("an unknown memory type lists the supported ones")
    void unknownMemoryTypeIsRejected() {
        assertThatThrownBy(() -> registryOf("""
                llm { SL { provider = fake-local, api-key = "k", model-name = "m"
                           memory { type = sliding-window, max-messages = 10 } } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("sliding-window")
                .hasMessageContaining("message-window")
                .hasMessageContaining("token-window");
    }

    private int fileCounter;

    private LlmRegistry registryOf(String hocon) throws IOException {
        // A counter, not a hash: Math.abs(Integer.MIN_VALUE) is still negative, and a hash
        // collision between two cases in one test would silently reuse a file.
        Path file = dir.resolve("test-" + (++fileCounter) + ".conf");
        Files.writeString(file, hocon, StandardCharsets.UTF_8);
        return LlmRegistry.builder().configFiles(List.of(file)).build();
    }
}
