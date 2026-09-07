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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code custom-properties} block: text the library carries and never interprets, and the
 * handler an application registers to turn it into an object of its own (ADR-0055).
 */
class CustomPropertiesTest {

    @TempDir
    Path dir;

    /** What an application might bind the block to. */
    record SupportProps(String promptId, int maxRetries) {

        static SupportProps parse(String json) {
            // A deliberately small reader, so the test depends on no binding library.
            return new SupportProps(field(json, "prompt-id"),
                    json.contains("max-retries") ? Integer.parseInt(field(json, "max-retries")) : 0);
        }

        private static String field(String json, String key) {
            int at = json.indexOf('"' + key + '"');
            if (at < 0) {
                return null;
            }
            String rest = json.substring(json.indexOf(':', at) + 1).trim();
            return rest.startsWith("\"")
                    ? rest.substring(1, rest.indexOf('"', 1))
                    : rest.split("[,}]")[0].trim();
        }
    }

    private static final String BLOCK = """
            llm.SL {
              provider = fake-local
              api-key = "k"
              model-name = "m"
              custom-properties { prompt-id = "support-v3", max-retries = 3 }
            }
            """;

    private static final String NO_BLOCK =
            "llm.SL { provider = fake-local, api-key = \"k\", model-name = \"m\" }\n";

    private Path file(String hocon) throws IOException {
        Path f = dir.resolve("c" + hocon.hashCode() + ".conf");
        Files.writeString(f, hocon, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    @DisplayName("the block reaches the application as JSON, and the handler's object is kept")
    void theHandlerSeesTheBlockAsJson() throws IOException {
        try (LlmRegistry<SupportProps> registry = LlmRegistry.builder()
                .configFiles(List.of(file(BLOCK)))
                .customPropertiesHandler(config -> SupportProps.parse(config.customPropertiesText()))
                .build()) {

            assertThat(registry.get("SL").customPropertiesText())
                    .isEqualTo("{\"max-retries\":3,\"prompt-id\":\"support-v3\"}");
            assertThat(registry.get("SL").customProperties())
                    .isEqualTo(new SupportProps("support-v3", 3));
        }
    }

    @Test
    @DisplayName("the reference's own example renders to the JSON the reference prints")
    void theDocumentedExampleRendersAsDocumented() throws IOException {
        // docs/manual/part-2-reference.md, "Values of your own", prints this output. A
        // duration is an interpretation HOCON applies when asked for one; JSON has no such
        // type, so 45s arrives as a string.
        Path f = file("llm.SUPPORT {\n  provider = fake-local\n  api-key = \"k\"\n"
                + "  model-name = \"m\"\n  custom-properties {\n"
                + "    prompt-id      = \"support-v3\"\n"
                + "    max-retries    = 3\n"
                + "    escalate-after = 45s\n  }\n}\n");

        try (LlmRegistry<Void> registry =
                LlmRegistry.builder().configFiles(List.of(f)).build()) {

            assertThat(registry.get("SUPPORT").customPropertiesText()).isEqualTo(
                    "{\"escalate-after\":\"45s\",\"max-retries\":3,\"prompt-id\":\"support-v3\"}");
        }
    }

    @Test
    @DisplayName("a configuration with no such block still reaches the handler, as {}")
    void anAbsentBlockStillReachesTheHandler() throws IOException {
        AtomicReference<String> seen = new AtomicReference<>();
        try (LlmRegistry<SupportProps> registry = LlmRegistry.builder()
                .configFiles(List.of(file(NO_BLOCK)))
                .customPropertiesHandler(config -> {
                    seen.set(config.customPropertiesText());
                    return SupportProps.parse(config.customPropertiesText());
                })
                .build()) {

            // A rule such as "an openai block needs a prompt id" is broken exactly here, so a
            // handler skipped for an absent block could not enforce the rule it exists for.
            assertThat(seen.get()).isEqualTo("{}");
            assertThat(registry.get("SL").customPropertiesText()).isEqualTo("{}");
            assertThat(registry.get("SL").customProperties()).isEqualTo(new SupportProps(null, 0));
        }
    }

    @Test
    @DisplayName("without a handler the text is still there, and the object is Void")
    void withoutAHandlerTheTextIsStillThere() throws IOException {
        try (LlmRegistry<Void> registry =
                LlmRegistry.builder().configFiles(List.of(file(BLOCK))).build()) {

            assertThat(registry.get("SL").customPropertiesText())
                    .isEqualTo("{\"max-retries\":3,\"prompt-id\":\"support-v3\"}");
            // Void has exactly one value, so the accessor is meaningless rather than an error.
            assertThat(registry.get("SL").customProperties()).isNull();
        }
    }

    @Test
    @DisplayName("a handler may reject, and the message names the block")
    void aHandlerMayReject() throws IOException {
        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file(BLOCK)))
                .customPropertiesHandler(config -> {
                    throw new IllegalArgumentException("max-retries must be under three");
                })
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("llm.SL")
                .hasMessageContaining("max-retries must be under three")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a handler rule can depend on the provider, which is why it takes the config")
    void aHandlerRuleCanDependOnTheProvider() throws IOException {
        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file(NO_BLOCK)))
                .customPropertiesHandler(config -> {
                    if ("fake-local".equals(config.provider())
                            && SupportProps.parse(config.customPropertiesText()).promptId() == null) {
                        throw new IllegalStateException("prompt-id is required for fake-local");
                    }
                    return config.name();
                })
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("prompt-id is required for fake-local");
    }

    @Test
    @DisplayName("editing only a custom property rebuilds that bundle and reports the name")
    void editingOnlyACustomPropertyIsAChange() throws Exception {
        Path f = file(BLOCK);
        try (LlmRegistry<SupportProps> registry = LlmRegistry.builder()
                .configFiles(List.of(f))
                .customPropertiesHandler(config -> SupportProps.parse(config.customPropertiesText()))
                .build()) {

            LlmBundle<SupportProps> before = registry.get("SL");
            Files.writeString(f, BLOCK.replace("support-v3", "support-v4"),
                    StandardCharsets.UTF_8);
            Optional<ReloadChange> change = registry.reload();

            assertThat(change).isPresent();
            assertThat(change.orElseThrow().updated()).containsExactly("SL");
            assertThat(registry.get("SL")).isNotSameAs(before);
            assertThat(registry.get("SL").customProperties().promptId()).isEqualTo("support-v4");
        }
    }

    @Test
    @DisplayName("an unchanged block keeps its bundle, and the handler is not run again")
    void anUnchangedBlockDoesNotRunTheHandlerAgain() throws Exception {
        Path f = file("llm.SL { provider = fake-local, api-key = \"k\", model-name = \"m\",\n"
                + "  custom-properties { prompt-id = \"support-v3\" } }\n"
                + "llm.SH { provider = fake-local, api-key = \"k\", model-name = \"m\" }\n");
        AtomicInteger runs = new AtomicInteger();

        try (LlmRegistry<SupportProps> registry = LlmRegistry.builder()
                .configFiles(List.of(f))
                .customPropertiesHandler(config -> {
                    runs.incrementAndGet();
                    return SupportProps.parse(config.customPropertiesText());
                })
                .build()) {
            assertThat(runs.get()).isEqualTo(2);
            LlmBundle<SupportProps> untouched = registry.get("SH");

            Files.writeString(f, Files.readString(f).replace("support-v3", "support-v4"),
                    StandardCharsets.UTF_8);
            registry.reload();

            // ADR-0006's carry-over reaches the handler's output as well: SH did not change,
            // so its bundle — and the object the handler made for it — is the same instance.
            assertThat(runs.get()).isEqualTo(3);
            assertThat(registry.get("SH")).isSameAs(untouched);
            assertThat(registry.get("SL").customProperties().promptId()).isEqualTo("support-v4");
        }
    }

    @Test
    @DisplayName("a reload the handler rejects changes nothing and keeps the previous snapshot")
    void aRejectedReloadChangesNothing() throws Exception {
        Path f = file(BLOCK);
        List<ReloadFailure> failures = new ArrayList<>();
        try (LlmRegistry<SupportProps> registry = LlmRegistry.builder()
                .configFiles(List.of(f))
                .customPropertiesHandler(config -> {
                    SupportProps props = SupportProps.parse(config.customPropertiesText());
                    if (props.maxRetries() > 5) {
                        throw new IllegalArgumentException("max-retries is too high");
                    }
                    return props;
                })
                .build()) {
            registry.onReloadFailure(failures::add);

            Files.writeString(f, BLOCK.replace("max-retries = 3", "max-retries = 99"),
                    StandardCharsets.UTF_8);

            assertThatThrownBy(registry::reload).isInstanceOf(ConfigValidationException.class);
            assertThat(registry.get("SL").customProperties().maxRetries()).isEqualTo(3);
            assertThat(failures).hasSize(1);
        }
    }

    @Test
    @DisplayName("a store the handler rejects writes nothing, on a layer that is not a file")
    void aRejectedStoreWritesNothing() throws IOException {
        StringBuilder row = new StringBuilder(BLOCK);
        WritableConfigSource layer = new WritableConfigSource() {
            @Override
            public String id() {
                return "llm_config#42";
            }

            @Override
            public String text() {
                return row.toString();
            }

            @Override
            public void write(String text) {
                row.setLength(0);
                row.append(text);
            }
        };

        try (LlmRegistry<SupportProps> registry = LlmRegistry.builder()
                .sources(List.of(layer))
                .customPropertiesHandler(config -> {
                    SupportProps props = SupportProps.parse(config.customPropertiesText());
                    if (props.maxRetries() > 5) {
                        throw new IllegalArgumentException("max-retries is too high");
                    }
                    return props;
                })
                .build()) {

            assertThatThrownBy(() -> registry.store(layer,
                    BLOCK.replace("max-retries = 3", "max-retries = 99")))
                    .isInstanceOf(ConfigValidationException.class);

            // Validated before it is written, so the layer still holds what it held.
            assertThat(row.toString()).isEqualTo(BLOCK);
            assertThat(registry.get("SL").customProperties().maxRetries()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("a higher layer overrides one property and clears another")
    void layeringWorksInsideTheBlock() throws IOException {
        Path base = file(BLOCK);
        Path higher = file("llm.SL.custom-properties {\n  prompt-id = \"experimental\"\n"
                + "  max-retries = null\n}\n");

        try (LlmRegistry<Void> registry =
                LlmRegistry.builder().configFiles(List.of(base, higher)).build()) {

            // ADR-0032's clearing idiom works inside the block, where it always has: what it
            // does not do is remove a whole configuration (ADR-0058).
            assertThat(registry.get("SL").customPropertiesText())
                    .isEqualTo("{\"prompt-id\":\"experimental\"}");
        }
    }

    @Test
    @DisplayName("a key cleared inside a nested block is dropped too")
    void clearingReachesEveryDepth() throws IOException {
        Path base = file("llm.SL { provider = fake-local, api-key = \"k\", model-name = \"m\"\n"
                + "  custom-properties { outer = 1, nested { keep = 2, drop = 3 } }\n}\n");
        Path higher = file("llm.SL.custom-properties.nested.drop = null\n");

        try (LlmRegistry<Void> registry =
                LlmRegistry.builder().configFiles(List.of(base, higher)).build()) {

            assertThat(registry.get("SL").customPropertiesText())
                    .isEqualTo("{\"nested\":{\"keep\":2},\"outer\":1}");
        }
    }

    @Test
    @DisplayName("a custom-properties that is not a block is refused, naming its type")
    void aNonObjectCustomPropertiesIsRefused() {
        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file("llm.SL { provider = fake-local, api-key = \"k\","
                        + " model-name = \"m\", custom-properties = 5 }\n")))
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("llm.SL.custom-properties")
                .hasMessageContaining("must be a block of values")
                .hasMessageContaining("NUMBER");
    }

    @Test
    @DisplayName("a misspelled key is refused, naming it with its layer and line")
    void aMisspelledKeyIsRefused() throws IOException {
        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file("llm.SL { provider = fake-local, api-key = \"k\",\n"
                        + "  model-name = \"m\", temperatur = 0.9 }\n")))
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("llm.SL has a key this library does not know")
                .hasMessageContaining("temperatur")
                .hasMessageContaining(".conf: 2");
    }

    @Test
    @DisplayName("every misspelled key is listed at once, not one per run")
    void everyMisspelledKeyIsListed() throws IOException {
        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file("llm.SL {\n  provider = fake-local\n"
                        + "  api-key = \"k\"\n  model-name = \"m\"\n  temperatur = 0.9\n"
                        + "  timeuot = 30s\n"
                        + "  memory { type = message-window, max-messages = 4, windo = 1 }\n}\n")))
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("3 keys this library does not know")
                // Leaf paths, so a misspelling inside memory is named where it lives.
                .hasMessageContaining("memory.windo")
                .hasMessageContaining("temperatur")
                .hasMessageContaining("timeuot");
    }

    @Test
    @DisplayName("the offending key is traced to the layer that introduced it")
    void theOffendingKeyNamesItsLayer() throws IOException {
        Path base = file("llm.SL { provider = fake-local, api-key = \"k\", model-name = \"m\" }\n");
        Path higher = file("llm.SL.timeuot = 30s\n");

        assertThatThrownBy(() -> LlmRegistry.builder().configFiles(List.of(base, higher)).build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("timeuot")
                .hasMessageContaining(higher.getFileName().toString());
    }

    @Test
    @DisplayName("anything under custom-properties is the application's, not a misspelling")
    void customPropertiesAreNeverUnknownKeys() throws IOException {
        try (LlmRegistry<Void> registry = LlmRegistry.builder()
                .configFiles(List.of(file("llm.SL { provider = fake-local, api-key = \"k\",\n"
                        + "  model-name = \"m\",\n"
                        + "  custom-properties { anything-at-all = 1, nested { deep = 2 } } }\n")))
                .build()) {

            assertThat(registry.get("SL").customPropertiesText())
                    .isEqualTo("{\"anything-at-all\":1,\"nested\":{\"deep\":2}}");
        }
    }

    @Test
    @DisplayName("a key cleared by a higher layer is not reported as unknown")
    void aClearedKeyIsNotAnUnknownKey() throws IOException {
        Path base = file("llm.SL { provider = fake-local, api-key = \"k\", model-name = \"m\",\n"
                + "  description = \"from the base\" }\n");
        Path higher = file("llm.SL.description = null\n");

        try (LlmRegistry<Void> registry =
                LlmRegistry.builder().configFiles(List.of(base, higher)).build()) {

            // ADR-0032's clearing idiom leaves the key present with a null value, which is
            // exactly the shape a stray key has; entrySet() is what tells them apart.
            assertThat(registry.get("SL").config().description()).isEmpty();
        }
    }

    @Test
    @DisplayName("a misspelling that also hides a required key names the misspelling")
    void aMisspelledRequiredKeyIsNamedRatherThanItsAbsence() throws IOException {
        // The parse collects a missing required value instead of stopping at it, so the
        // recorded set of known keys is complete and modle-name is reported rather than
        // "no configuration setting found for model-name".
        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file("llm.SL { provider = fake-local, api-key = \"k\",\n"
                        + "  modle-name = \"m\" }\n")))
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("modle-name");
    }

    @Test
    @DisplayName("a required key that is simply absent gives the message it always gave")
    void anAbsentRequiredKeyIsStillReportedAsMissing() throws IOException {
        // The exact text, not merely the key's name: the record's own validation would also
        // mention model-name, so a looser assertion passes whether or not the parse hands the
        // failure back to Typesafe Config. The tutorial prints this line as real output.
        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file("llm.SL { provider = fake-local, api-key = \"k\" }\n")))
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("No configuration setting found for key 'model-name'");
    }

    @Test
    @DisplayName("a block that sets every optional key is accepted, key by key")
    void everyOptionalKeyIsKnown() throws IOException {
        // One block using all of them, because the schema's known keys are whatever the parse
        // asks for: a reader that stopped recording one accessor would turn the keys read
        // through it into unknown ones, and only a block that sets them can notice.
        Path f = file("llm.SL {\n"
                + "  description = \"a note\"\n"
                + "  provider = fake-local\n  api-key = \"k\"\n  model-name = \"m\"\n"
                + "  temperature = 0.5\n  timeout = 30s\n"
                + "  log-requests = true\n  log-responses = true\n  streaming = true\n"
                + "  memory { type = token-window, max-tokens = 10,"
                + " allow-remote-token-counting = false }\n"
                + "  moderation.enabled = false\n"
                + "  custom-properties { a = 1 }\n}\n");

        try (LlmRegistry<Void> registry =
                LlmRegistry.builder().configFiles(List.of(f)).build()) {

            LlmConfig config = registry.get("SL").config();
            assertThat(config.description()).contains("a note");
            assertThat(config.temperature()).contains(0.5);
            assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(config.logRequests()).isTrue();
            assertThat(config.memory()).isPresent();
            assertThat(config.customPropertiesText()).isEqualTo("{\"a\":1}");
        }
    }

    @Test
    @DisplayName("the other memory shape is known too")
    void theMessageWindowKeysAreKnown() throws IOException {
        Path f = file("llm.SL { provider = fake-local, api-key = \"k\", model-name = \"m\",\n"
                + "  memory { type = message-window, max-messages = 4 } }\n");

        try (LlmRegistry<Void> registry =
                LlmRegistry.builder().configFiles(List.of(f)).build()) {
            assertThat(registry.get("SL").config().memory()).isPresent();
        }
    }

    @Test
    @DisplayName("an interrupted handler restores the flag rather than swallowing it")
    void anInterruptedHandlerRestoresTheFlag() throws IOException {
        Path f = file(BLOCK);
        try {
            assertThatThrownBy(() -> LlmRegistry.builder()
                    .configFiles(List.of(f))
                    .customPropertiesHandler(config -> {
                        throw new InterruptedException("cancelled");
                    })
                    .build())
                    .isInstanceOf(ConfigValidationException.class)
                    .hasMessageContaining("was interrupted");

            // build() and reload() run on the caller's thread, so losing the flag here would
            // discard a cancellation that thread is acting on.
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();   // clear it, so the rest of the suite is unaffected
        }
    }

    @Test
    @DisplayName("registering the handler last keeps everything the builder was already given")
    void theTypeChangingMethodKeepsWhatWasSet() throws IOException {
        // The obvious implementation of a type-changing builder method returns a fresh
        // Builder<U> and drops the sources, watch, debounce and notifier already set.
        try (LlmRegistry<SupportProps> registry = LlmRegistry.builder()
                .configFiles(List.of(file(BLOCK)))
                .debounce(Duration.ofMillis(50))
                .watch(true)
                .customPropertiesHandler(config -> SupportProps.parse(config.customPropertiesText()))
                .build()) {

            assertThat(registry.names()).containsExactly("SL");
            assertThat(registry.get("SL").customProperties().promptId()).isEqualTo("support-v3");
        }
    }
}
