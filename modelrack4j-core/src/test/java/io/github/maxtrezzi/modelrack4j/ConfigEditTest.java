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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Writing a configuration layer back: ConfigEdit and WritableConfigSource. */
class ConfigEditTest {

    @TempDir
    private Path dir;

    private static final String SECRET_VALUE = "sk-not-a-real-key-12345";

    /**
     * The secret lives in its own lower layer, never in the layer being edited. That keeps
     * the suite offline — CI runs with nothing set — while leaving the edited layer holding
     * only {@code ${secret}}, which is the property these tests are about.
     */
    private static final ConfigSource SECRETS =
            ConfigSource.of("secrets", "secret = \"" + SECRET_VALUE + "\"");

    private static final String LAYER = """
            # a comment that must survive
            llm {
              SL {
                provider    = fake-local
                api-key     = ${secret}
                model-name  = "first"
              }
            }
            """;

    /** A writable layer held in memory, standing in for a database row. */
    private static final class MemoryRow implements WritableConfigSource {
        private final AtomicReference<String> stored;
        private final AtomicInteger writes = new AtomicInteger();
        private volatile boolean refuseWrites;

        MemoryRow(String text) {
            this.stored = new AtomicReference<>(text);
        }

        @Override
        public String id() {
            return "row#1";
        }

        @Override
        public String text() {
            return stored.get();
        }

        @Override
        public void write(String text) {
            if (refuseWrites) {
                throw new ConfigValidationException("the database is unreachable");
            }
            writes.incrementAndGet();
            stored.set(text);
        }
    }

    private Path writeLayer(String name, String text) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private LlmRegistry registryOver(ConfigSource... layers) {
        List<ConfigSource> all = new ArrayList<>();
        all.add(SECRETS);
        all.addAll(List.of(layers));
        return LlmRegistry.builder().sources(all).build();
    }

    @Test
    @DisplayName("an edit applies the change and stores it")
    void anEditAppliesAndStores() throws IOException {
        Path file = writeLayer("app.conf", LAYER);
        WritableConfigSource target = ConfigSource.ofWritableFile(file);

        try (LlmRegistry registry = registryOver(target)) {
            Optional<ReloadChange> change =
                    registry.edit(target).set("SL.model-name", "second").commit();

            assertThat(change).isPresent();
            assertThat(change.get().updated()).containsExactly("SL");
            assertThat(registry.get("SL").config().modelName()).isEqualTo("second");
            assertThat(Files.readString(file, StandardCharsets.UTF_8))
                    .contains("model-name=second");
        }
    }

    @Test
    @DisplayName("the stored text keeps the substitution, so no secret is ever written")
    void theStoredTextNeverHoldsTheSecret() throws IOException {
        Path file = writeLayer("app.conf", LAYER);
        WritableConfigSource target = ConfigSource.ofWritableFile(file);

        try (LlmRegistry registry = registryOver(target)) {
            // What the application holds is resolved: this is the value that must not leak.
            String resolved = registry.get("SL").config().apiKey();
            assertThat(resolved).isEqualTo(SECRET_VALUE);

            registry.edit(target).set("SL.model-name", "second").commit();

            String stored = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(stored).contains("${secret}");
            assertThat(stored).doesNotContain(resolved);
        }
    }

    @Test
    @DisplayName("setSubstitution writes a substitution, not a quoted string")
    void setSubstitutionWritesASubstitution() throws IOException {
        Path file = writeLayer("app.conf", LAYER);
        WritableConfigSource target = ConfigSource.ofWritableFile(file);

        try (LlmRegistry registry = registryOver(target)) {
            registry.edit(target)
                    .set("SH", Map.of("provider", "fake-local", "model-name", "added"))
                    .setSubstitution("SH.api-key", "secret")
                    .commit();

            String stored = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(stored).contains("${secret}");
            assertThat(stored).doesNotContain("\"${secret}\"");
            assertThat(registry.names()).contains("SH");
            // and it resolves to the same value the other block gets
            assertThat(registry.get("SH").config().apiKey())
                    .isEqualTo(registry.get("SL").config().apiKey());
        }
    }

    @Test
    @DisplayName("removing a key in the top layer uncovers the one below it")
    void removingUncoversTheLayerBelow() throws IOException {
        Path base = writeLayer("base.conf", """
                llm { SL { provider = fake-local, api-key = ${secret}
                           model-name = "from-base" } }
                """);
        Path top = writeLayer("top.conf", "llm { SL { model-name = \"from-top\" } }");
        WritableConfigSource target = ConfigSource.ofWritableFile(top);

        try (LlmRegistry registry =
                registryOver(ConfigSource.ofFile(base), target)) {
            assertThat(registry.get("SL").config().modelName()).isEqualTo("from-top");

            registry.edit(target).remove("SL.model-name").commit();

            assertThat(registry.get("SL").config().modelName()).isEqualTo("from-base");
        }
    }

    @Test
    @DisplayName("an edit that would not validate changes nothing and stores nothing")
    void anInvalidEditChangesNothing() throws IOException {
        Path file = writeLayer("app.conf", LAYER);
        WritableConfigSource target = ConfigSource.ofWritableFile(file);
        String before = Files.readString(file, StandardCharsets.UTF_8);

        try (LlmRegistry registry = registryOver(target)) {
            assertThatThrownBy(() ->
                    registry.edit(target).set("SL.provider", "not-a-provider").commit())
                    .isInstanceOf(ConfigValidationException.class)
                    .hasMessageContaining("not-a-provider");

            assertThat(registry.get("SL").config().provider()).isEqualTo("fake-local");
            assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(before);
        }
    }

    @Test
    @DisplayName("a write that fails puts the previous configuration back")
    void aFailedWriteRollsBack() {
        MemoryRow row = new MemoryRow(LAYER);

        try (LlmRegistry registry = registryOver(row)) {
            row.refuseWrites = true;

            assertThatThrownBy(() ->
                    registry.edit(row).set("SL.model-name", "second").commit())
                    .isInstanceOf(ConfigValidationException.class)
                    .hasMessageContaining("unreachable");

            assertThat(registry.get("SL").config().modelName()).isEqualTo("first");
            assertThat(row.text()).isEqualTo(LAYER);
            assertThat(row.writes).hasValue(0);
        }
    }

    @Test
    @DisplayName("an edit fires no reload listener: the caller already knows")
    void anEditFiresNoListener() {
        MemoryRow row = new MemoryRow(LAYER);
        AtomicInteger reloads = new AtomicInteger();

        try (LlmRegistry registry = registryOver(row)) {
            registry.onReload(change -> reloads.incrementAndGet());

            registry.edit(row).set("SL.model-name", "second").commit();

            assertThat(registry.get("SL").config().modelName()).isEqualTo("second");
            assertThat(reloads).hasValue(0);
        }
    }

    @Test
    @DisplayName("a watcher that wakes after an edit finds nothing to publish")
    void theWatcherPublishesNothingAfterAnEdit() throws Exception {
        Path file = writeLayer("app.conf", LAYER);
        WritableConfigSource target = ConfigSource.ofWritableFile(file);
        AtomicInteger reloads = new AtomicInteger();

        try (LlmRegistry registry = LlmRegistry.builder()
                .sources(List.of(SECRETS, target))
                .notifier(FileChangeNotifier.of(List.of(file), Duration.ofMillis(50)))
                .build()) {
            registry.onReload(change -> reloads.incrementAndGet());

            registry.edit(target).set("SL.model-name", "second").commit();

            // Long enough for the debounce to expire and the watcher to have re-read.
            Thread.sleep(600);
            assertThat(registry.get("SL").config().modelName()).isEqualTo("second");
            assertThat(reloads).as("the application's own edit must raise no event")
                    .hasValue(0);
        }
    }

    @Test
    @DisplayName("a layer that uses include is refused rather than silently losing it")
    void aLayerWithAnIncludeIsRefused() throws IOException {
        writeLayer("included.conf", "llm { SL { model-name = \"from-include\" } }");
        Path file = writeLayer("app.conf", """
                include "included.conf"
                llm { SL { provider = fake-local, api-key = ${secret} } }
                """);
        WritableConfigSource target = ConfigSource.ofWritableFile(file);
        String before = Files.readString(file, StandardCharsets.UTF_8);

        try (LlmRegistry registry = registryOver(target)) {
            // The include works for reading: this is what makes losing it on a write bad.
            assertThat(registry.get("SL").config().modelName()).isEqualTo("from-include");

            assertThatThrownBy(() ->
                    registry.edit(target).set("SL.temperature", 0.5).commit())
                    .isInstanceOf(ConfigValidationException.class)
                    .hasMessageContaining("include");

            assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(before);
            assertThat(registry.get("SL").config().modelName()).isEqualTo("from-include");
        }
    }

    @Test
    @DisplayName("an editable layer above an including one is edited normally")
    void anEditableLayerAboveAnIncludingOne() throws IOException {
        writeLayer("included.conf", "llm { SL { model-name = \"from-include\" } }");
        Path base = writeLayer("base.conf", """
                include "included.conf"
                llm { SL { provider = fake-local, api-key = ${secret} } }
                """);
        Path top = writeLayer("top.conf", "llm { }");
        WritableConfigSource target = ConfigSource.ofWritableFile(top);

        try (LlmRegistry registry = registryOver(ConfigSource.ofFile(base), target)) {
            registry.edit(target).set("SL.model-name", "from-top").commit();

            assertThat(registry.get("SL").config().modelName()).isEqualTo("from-top");
            // the including layer is untouched, so its include still works
            assertThat(Files.readString(base, StandardCharsets.UTF_8))
                    .contains("include \"included.conf\"");
        }
    }

    @Test
    @DisplayName("text() gives the stored text, unresolved")
    void textIsUnresolved() {
        MemoryRow row = new MemoryRow(LAYER);
        try (LlmRegistry registry = registryOver(row)) {
            assertThat(registry.edit(row).text())
                    .contains("${secret}")
                    .contains("# a comment that must survive");
        }
    }

    @Test
    @DisplayName("a layer this registry does not read cannot be edited through it")
    void aForeignLayerIsRefused() {
        MemoryRow mine = new MemoryRow(LAYER);
        MemoryRow theirs = new MemoryRow(LAYER);   // a different instance, same id
        try (LlmRegistry registry = registryOver(mine)) {
            assertThatThrownBy(() -> registry.edit(theirs))
                    .isInstanceOf(ConfigValidationException.class)
                    .hasMessageContaining("not one of");
        }
    }

    @Test
    @DisplayName("an edit with no operations is refused rather than rewriting the file")
    void anEmptyEditIsRefused() {
        MemoryRow row = new MemoryRow(LAYER);
        try (LlmRegistry registry = registryOver(row)) {
            assertThatThrownBy(() -> registry.edit(row).commit())
                    .isInstanceOf(ConfigValidationException.class)
                    .hasMessageContaining("changes nothing");
            assertThat(row.writes).hasValue(0);
        }
    }
}
