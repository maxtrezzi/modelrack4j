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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import io.github.maxtrezzi.modelrack4j.testing.FakeStrictProviderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Building the registry: defaults, lookup, provider discovery and the capability rules. */
class LlmRegistryTest {

    /** The tail of the unknown-provider message is the sorted list of provider ids. */
    private static final String AVAILABLE = "Available providers: ";

    @TempDir
    Path dir;

    /** Every registry a test built, so none is left open when it ends. */
    private final List<LlmRegistry> built = new ArrayList<>();

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

        UnknownConfigurationException thrown = catchThrowableOfType(
                UnknownConfigurationException.class, () -> registry.get("NOPE"));

        // The name is on the exception as well as in its message, so a caller can branch on
        // it without parsing prose.
        assertThat(thrown).hasMessageContaining("NOPE");
        assertThat(thrown.configurationName()).isEqualTo("NOPE");
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
    @DisplayName("the providers are listed in a fixed order, so the message does not vary by run")
    void availableProvidersAreListedInOrder() {
        // ServiceLoader does not specify its order, so without sorting the same mistake
        // produces a differently ordered message on another machine or another JVM.
        Throwable thrown = catchThrowable(() -> registryOf("""
                llm { SL { provider = nope, api-key = "k", model-name = "m" } }
                """));

        String message = thrown.getMessage();
        String listed = message.substring(message.indexOf(AVAILABLE) + AVAILABLE.length());
        assertThat(listed.split(", ")).hasSizeGreaterThan(1).isSorted();
    }

    @Test
    @DisplayName("a factory that never heard of supportsModeration still refuses moderation, "
            + "from the build step")
    void moderationOnALegacyFactoryIsCaughtDownstream() {
        // ADR-0048's compatibility claim, which is the whole reason the SPI default is `true`
        // rather than `false`. FakeLegacyProviderFactory does not override the method, so core
        // lets the configuration past the capability check — and the missing model is caught a
        // moment later instead. "produced no", not "ships no": the two messages name different
        // failures, and this is the one a factory written before the method existed produces.
        assertThatThrownBy(() -> registryOf("""
                llm { SL { provider = fake-legacy, api-key = "k", model-name = "m"
                           moderation { enabled = true } } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("produced no moderation model");
    }

    @Test
    @DisplayName("a provider without moderation rejects a config that enables it")
    void moderationRejectedByProvider() {
        // "ships no" rather than just "moderation". The looser version passed either way:
        // when the config reaches the build step anyway, the missing model is reported
        // downstream as "produced no moderation model", which also contains the word, so the
        // test could not tell the two failures apart. Those two words are the whole
        // difference between the capability check refusing this up front and the build step
        // catching it afterwards, and only the first is what this test is about.
        assertThatThrownBy(() -> registryOf("""
                llm { SL { provider = fake-remote, api-key = "k", model-name = "m"
                           moderation { enabled = true } } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("fake-remote")
                .hasMessageContaining("ships no moderation model");
    }

    @Test
    @DisplayName("an objection only the factory can raise reaches the caller")
    void factoryValidationIsApplied() {
        // Which model names exist is provider knowledge, so this rejection can come from
        // nowhere but ProviderFactory.validate. It is what pins the SPI contract: no
        // capability rule in core covers this case, so if the call went away nothing else
        // would fail.
        assertThatThrownBy(() -> registryOf("""
                llm { SL { provider = fake-strict, api-key = "k", model-name = "made-up" } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("fake-strict")
                .hasMessageContaining("does not offer model")
                .hasMessageContaining("made-up");
    }

    @Test
    @DisplayName("a configuration the factory accepts is built")
    void factoryValidationAcceptsWhatItAllows() throws IOException {
        var registry = registryOf("llm { SL { provider = fake-strict, api-key = \"k\""
                + ", model-name = \"" + FakeStrictProviderFactory.SUPPORTED_MODEL + "\" } }");

        assertThat(registry.get("SL").chatModel()).isNotNull();
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

        // The estimator is the same bug one layer down: the capability rules in core pass,
        // because the factory reports LOCAL, and the memory then cannot be built.
        assertThatThrownBy(() -> registryOf("""
                llm { SL { provider = fake-incomplete, api-key = "k", model-name = "m"
                           memory { type = token-window, max-tokens = 500 } } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("fake-incomplete")
                .hasMessageContaining("produced no token count estimator");
    }

    @Test
    @DisplayName("a null Optional from any of the three optional capabilities is a config error")
    void nullOptionalFromAnyCapabilityIsReportedAsAConfigError() {
        // The SPI declares these three as Optional, so null breaks it. All three go through
        // requireProduced, which checks for null before empty — the estimator did not, and a
        // factory returning null there produced a NullPointerException naming
        // Optional.orElseThrow instead of a message naming the block and the provider.
        assertThatThrownBy(() -> registryOf("llm { SL { provider = fake-null-optional"
                + ", api-key = \"k\", model-name = \"m\", streaming = true } }"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("llm.SL")
                .hasMessageContaining("fake-null-optional")
                .hasMessageContaining("produced no streaming chat model");

        assertThatThrownBy(() -> registryOf("llm { SL { provider = fake-null-optional"
                + ", api-key = \"k\", model-name = \"m\", moderation { enabled = true } } }"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("llm.SL")
                .hasMessageContaining("fake-null-optional")
                .hasMessageContaining("produced no moderation model");

        assertThatThrownBy(() -> registryOf("""
                llm { SL { provider = fake-null-optional, api-key = "k", model-name = "m"
                           memory { type = token-window, max-tokens = 500 } } }
                """))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("llm.SL")
                .hasMessageContaining("fake-null-optional")
                .hasMessageContaining("produced no token count estimator");
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
        // The convenience accessor has to agree with the key the bundle was found under.
        assertThat(registry.get("we\"ird").name()).isEqualTo("we\"ird");
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

    /**
     * A provider is built from configuration and then handed out. Only calling it shows that
     * the configured bound reached the memory it produces: asserting that the provider is
     * present leaves the bound itself unverified.
     */
    @Nested
    @DisplayName("the memory a bundle hands out")
    class MemoryIsUsable {

        @Test
        @DisplayName("message-window memory keeps the configured number of messages")
        void messageWindowHonoursItsBound() throws IOException {
            var registry = registryOf("""
                    llm { SL { provider = fake-local, api-key = "k", model-name = "m"
                               memory { type = message-window, max-messages = 3 } } }
                    """);

            ChatMemory memory = memoryOf(registry, "conversation-1");
            addMessages(memory, 10);

            assertThat(memory.id()).isEqualTo("conversation-1");
            assertThat(memory.messages()).hasSize(3);
        }

        @Test
        @DisplayName("token-window memory stays inside the configured token budget")
        void tokenWindowHonoursItsBound() throws IOException {
            // FakeTokenCountEstimator charges one token per message, so a budget of three
            // tokens cannot hold more than three of these messages.
            var registry = registryOf("""
                    llm { SL { provider = fake-local, api-key = "k", model-name = "m"
                               memory { type = token-window, max-tokens = 3 } } }
                    """);

            ChatMemory memory = memoryOf(registry, "conversation-2");
            addMessages(memory, 10);

            assertThat(memory.id()).isEqualTo("conversation-2");
            assertThat(memory.messages()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        }

        private ChatMemory memoryOf(LlmRegistry registry, Object memoryId) {
            return registry.get("SL").chatMemoryProvider()
                    .orElseThrow(() -> new AssertionError("SL has no chat memory provider"))
                    .get(memoryId);
        }

        private void addMessages(ChatMemory memory, int count) {
            for (int i = 1; i <= count; i++) {
                memory.add(UserMessage.from("message " + i));
            }
        }
    }

    private int fileCounter;

    private LlmRegistry registryOf(String hocon) throws IOException {
        // A counter, not a hash: Math.abs(Integer.MIN_VALUE) is still negative, and a hash
        // collision between two cases in one test would silently reuse a file.
        Path file = dir.resolve("test-" + (++fileCounter) + ".conf");
        Files.writeString(file, hocon, StandardCharsets.UTF_8);
        LlmRegistry registry = LlmRegistry.builder().configFiles(List.of(file)).build();
        built.add(registry);
        return registry;
    }

    @AfterEach
    void closeRegistries() {
        built.forEach(LlmRegistry::close);
    }
}
