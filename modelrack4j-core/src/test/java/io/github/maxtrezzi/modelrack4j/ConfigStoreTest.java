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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Storing a configuration layer back: {@link LlmRegistry#store(WritableConfigSource, String)}
 * and {@link WritableConfigSource}.
 */
class ConfigStoreTest {

    @TempDir
    private Path dir;

    private static final String SECRET_VALUE = "sk-not-a-real-key-12345";

    /**
     * The secret lives in its own lower layer, never in the layer being stored. That keeps
     * the suite offline — CI runs with nothing set — while leaving the stored layer holding
     * only {@code ${secret}}, which is the property these tests are about.
     */
    private static final ConfigSource SECRETS =
            ConfigSource.of("secrets", "secret = \"" + SECRET_VALUE + "\"");

    private static final String LAYER = """
            # a comment the application chose to keep
            llm {
              SL {
                provider    = fake-local
                api-key     = ${secret}
                model-name  = "first"
              }
            }
            """;

    private static final String LAYER_WITH_SECOND_MODEL = """
            llm {
              SL {
                provider    = fake-local
                api-key     = ${secret}
                model-name  = "second"
              }
            }
            """;

    /** The prefix {@code WritableFileConfigSource} gives a staged file. */
    private static final String STAGE_PREFIX = ".modelrack4j-staged-";

    /** How often a writer may rebase before the test calls it a defect rather than a race. */
    private static final int MAX_ATTEMPTS = 100;

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
                // The medium failed, not the text: that is what ConfigAccessException means,
                // and what WritableConfigSource.write tells an implementer to throw.
                throw new ConfigAccessException("the database is unreachable");
            }
            writes.incrementAndGet();
            stored.set(text);
        }
    }

    private Path writeLayer(String name, String text) throws IOException {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private LlmRegistry registryOver(ConfigSource... layers) {
        List<ConfigSource> all = new ArrayList<>();
        all.add(SECRETS);
        all.addAll(List.of(layers));
        return LlmRegistry.builder().sources(all).build();
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** Every staged file left behind in a directory tree. There should never be one. */
    private List<Path> stagedFilesUnder(Path root) throws IOException {
        try (Stream<Path> tree = Files.walk(root)) {
            return tree.filter(p -> p.getFileName().toString().startsWith(STAGE_PREFIX))
                    .toList();
        }
    }

    @Nested
    @DisplayName("what a store does when it succeeds")
    class WhenItSucceeds {

        @Test
        @DisplayName("it applies the new text and writes it")
        void store_appliesAndWrites() throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            try (LlmRegistry registry = registryOver(target)) {
                Optional<ReloadChange> change =
                        registry.store(target, LAYER_WITH_SECOND_MODEL);

                assertThat(change).isPresent();
                assertThat(change.get().updated()).containsExactly("SL");
                assertThat(change.get().added()).isEmpty();
                assertThat(change.get().removed()).isEmpty();
                assertThat(registry.get("SL").config().modelName()).isEqualTo("second");
            }
        }

        @Test
        @DisplayName("it writes the text exactly as given, comments and layout included")
        void store_writesTheTextVerbatim() throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);
            String formatted = """
                    # a comment the application chose to keep
                    llm {
                      SL {
                        provider    = fake-local
                        api-key     = ${secret}
                        model-name  = "second"      # aligned on purpose
                      }
                    }
                    """;

            try (LlmRegistry registry = registryOver(target)) {
                registry.store(target, formatted);
            }

            assertThat(read(file)).isEqualTo(formatted);
        }

        @Test
        @DisplayName("it keeps a substitution unresolved, so no secret is written")
        void store_keepsSubstitutionsUnresolved() throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            try (LlmRegistry registry = registryOver(target)) {
                // What the application holds is resolved: this is the value that must not leak.
                assertThat(registry.get("SL").config().apiKey()).isEqualTo(SECRET_VALUE);

                registry.store(target, LAYER_WITH_SECOND_MODEL);

                assertThat(read(file)).contains("${secret}").doesNotContain(SECRET_VALUE);
                assertThat(registry.get("SL").config().apiKey()).isEqualTo(SECRET_VALUE);
            }
        }

        @Test
        @DisplayName("a key left out of the top layer uncovers the one below it")
        void store_omittingAKeyUncoversTheLayerBelow() throws IOException {
            Path base = writeLayer("base.conf", """
                    llm { SL { provider = fake-local, api-key = ${secret}
                               model-name = "from-base" } }
                    """);
            Path top = writeLayer("top.conf", "llm { SL { model-name = \"from-top\" } }");
            WritableConfigSource target = ConfigSource.ofWritableFile(top);

            try (LlmRegistry registry = registryOver(ConfigSource.ofFile(base), target)) {
                assertThat(registry.get("SL").config().modelName()).isEqualTo("from-top");

                registry.store(target, "llm { }");

                assertThat(registry.get("SL").config().modelName()).isEqualTo("from-base");
            }
        }

        @Test
        @DisplayName("a block the text adds is reported as added")
        void store_addingABlockIsReportedAsAdded() throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            try (LlmRegistry registry = registryOver(target)) {
                Optional<ReloadChange> change = registry.store(target, LAYER + """
                        llm.SH {
                          provider   = fake-local
                          api-key    = ${secret}
                          model-name = "added"
                        }
                        """);

                assertThat(change).isPresent();
                assertThat(change.get().added()).containsExactly("SH");
                assertThat(change.get().updated()).isEmpty();
                assertThat(registry.names()).containsExactlyInAnyOrder("SL", "SH");
            }
        }

        @Test
        @DisplayName("a block the text leaves out is removed, and get() then refuses the name")
        void store_removingABlockRemovesTheConfiguration() throws IOException {
            Path file = writeLayer("app.conf", LAYER + """
                    llm.SH {
                      provider   = fake-local
                      api-key    = ${secret}
                      model-name = "second"
                    }
                    """);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            try (LlmRegistry registry = registryOver(target)) {
                Optional<ReloadChange> change = registry.store(target, LAYER);

                assertThat(change).isPresent();
                assertThat(change.get().removed()).containsExactly("SH");
                assertThatThrownBy(() -> registry.get("SH"))
                        .isInstanceOf(UnknownConfigurationException.class);
            }
        }

        @Test
        @DisplayName("a text that only reformats is stored, and reported as no change")
        void store_withTheSameMeaningIsWrittenAndReportsNothing() throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);
            String reformatted =
                    "llm.SL { provider = fake-local, api-key = ${secret}, model-name = \"first\" }\n";

            try (LlmRegistry registry = registryOver(target)) {
                Optional<ReloadChange> change = registry.store(target, reformatted);

                assertThat(change).as("the parsed configuration is the same").isEmpty();
                assertThat(read(file)).as("the text is still what the caller asked to store")
                        .isEqualTo(reformatted);
            }
        }

        @Test
        @DisplayName("a layer that is not a file is staged as text and written once")
        void store_stagesANonFileLayerAsText() {
            MemoryRow row = new MemoryRow(LAYER);

            try (LlmRegistry registry = registryOver(row)) {
                registry.store(row, LAYER_WITH_SECOND_MODEL);

                assertThat(row.writes).hasValue(1);
                assertThat(row.text()).isEqualTo(LAYER_WITH_SECOND_MODEL);
                assertThat(registry.get("SL").config().modelName()).isEqualTo("second");
            }
        }
    }

    @Nested
    @DisplayName("what a store does when it is refused")
    class WhenItIsRefused {

        @Test
        @DisplayName("a text that does not validate changes nothing and writes nothing")
        void store_withAnInvalidConfiguration_changesNothing() throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);
            String before = read(file);

            try (LlmRegistry registry = registryOver(target)) {
                assertThatThrownBy(() -> registry.store(target,
                        LAYER.replace("fake-local", "not-a-provider")))
                        .isInstanceOf(ConfigValidationException.class)
                        .hasMessageContaining("not-a-provider");

                assertThat(registry.get("SL").config().provider()).isEqualTo("fake-local");
                assertThat(read(file)).isEqualTo(before);
            }
        }

        @Test
        @DisplayName("a text that does not parse changes nothing and writes nothing")
        void store_withUnparseableText_changesNothing() throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);
            String before = read(file);

            try (LlmRegistry registry = registryOver(target)) {
                assertThatThrownBy(() -> registry.store(target, "llm { SL { unclosed = "))
                        .isInstanceOf(ConfigValidationException.class);

                assertThat(registry.get("SL").config().modelName()).isEqualTo("first");
                assertThat(read(file)).isEqualTo(before);
            }
        }

        @Test
        @DisplayName("a directory that cannot be written fails before validation, as access")
        void store_ontoAReadOnlyDirectory_failsAsAccessNotValidation() throws IOException {
            Path locked = Files.createDirectory(dir.resolve("locked"));
            Path file = locked.resolve("app.conf");
            Files.writeString(file, LAYER, StandardCharsets.UTF_8);
            // Both conditions are needed: a filesystem with no POSIX permissions cannot set
            // them at all, and root ignores them once set, so the store would succeed and
            // the test would fail for a reason that says nothing about the code.
            Assumptions.assumeTrue(
                    Files.getFileAttributeView(locked, PosixFileAttributeView.class) != null,
                    "POSIX permissions are not a concept on this filesystem");
            Assumptions.assumeFalse("root".equals(System.getProperty("user.name")),
                    "root ignores the permissions this test relies on");
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            try (LlmRegistry registry = registryOver(target)) {
                // Read and executable, not writable: the layer still loads, and staging beside
                // it cannot. That is the commonest storage failure, and it happens before the
                // new text is validated at all.
                Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"));
                try {
                    assertThatThrownBy(() -> registry.store(target, LAYER_WITH_SECOND_MODEL))
                            .isInstanceOf(ConfigAccessException.class)
                            .isNotInstanceOf(ConfigValidationException.class)
                            // The message has to name the directory and say that it is what
                            // needs write permission. The cause names the staged file, which
                            // by now has been removed again and which the caller has never
                            // seen, so on its own it sends the reader nowhere.
                            .hasMessageContaining("Cannot write the configuration " + file)
                            .hasMessageContaining("temporary file in " + locked)
                            .hasMessageContaining(
                                    "needs that directory to be writable, not only the file");

                    assertThat(registry.get("SL").config().modelName()).isEqualTo("first");
                } finally {
                    Files.setPosixFilePermissions(locked,
                            PosixFilePermissions.fromString("rwxr-xr-x"));
                }
                assertThat(read(file)).isEqualTo(LAYER);
                assertThat(stagedFilesUnder(dir)).isEmpty();
            }
        }

        @Test
        @DisplayName("a write that fails puts the previous configuration back")
        void store_whenTheWriteFails_rollsBack() {
            MemoryRow row = new MemoryRow(LAYER);

            try (LlmRegistry registry = registryOver(row)) {
                row.refuseWrites = true;

                assertThatThrownBy(() -> registry.store(row, LAYER_WITH_SECOND_MODEL))
                        .isInstanceOf(ConfigAccessException.class)
                        .hasMessageContaining("unreachable");

                assertThat(registry.get("SL").config().modelName()).isEqualTo("first");
                assertThat(row.text()).isEqualTo(LAYER);
                assertThat(row.writes).hasValue(0);
            }
        }

        @Test
        @DisplayName("a layer this registry does not read cannot be stored through it")
        void store_withAForeignLayer_isRefused() {
            MemoryRow mine = new MemoryRow(LAYER);
            MemoryRow theirs = new MemoryRow(LAYER);   // a different instance, same id

            try (LlmRegistry registry = registryOver(mine)) {
                assertThatThrownBy(() -> registry.store(theirs, LAYER_WITH_SECOND_MODEL))
                        .isInstanceOf(ConfigValidationException.class)
                        .hasMessageContaining("not one of");

                assertThat(theirs.writes).hasValue(0);
            }
        }

        @Test
        @DisplayName("neither argument may be null")
        void store_withNullArguments_throws() {
            MemoryRow row = new MemoryRow(LAYER);

            try (LlmRegistry registry = registryOver(row)) {
                assertThatNullPointerException()
                        .isThrownBy(() -> registry.store(null, LAYER));
                assertThatNullPointerException()
                        .isThrownBy(() -> registry.store(row, null));
                assertThat(row.writes).hasValue(0);
            }
        }

        @Test
        @DisplayName("a refused store leaves no staged file behind")
        void store_whenRefused_leavesNoStagedFile() throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            try (LlmRegistry registry = registryOver(target)) {
                assertThatThrownBy(() -> registry.store(target,
                        LAYER.replace("fake-local", "not-a-provider")))
                        .isInstanceOf(ConfigValidationException.class);
                registry.store(target, LAYER_WITH_SECOND_MODEL);
            }

            assertThat(stagedFilesUnder(dir)).isEmpty();
        }
    }

    @Nested
    @DisplayName("what a store tells listeners")
    class WhatListenersHear {

        @Test
        @DisplayName("a store fires no reload listener: the caller already knows")
        void store_firesNoReloadListener() {
            MemoryRow row = new MemoryRow(LAYER);
            AtomicInteger reloads = new AtomicInteger();

            try (LlmRegistry registry = registryOver(row)) {
                registry.onReload(change -> reloads.incrementAndGet());

                registry.store(row, LAYER_WITH_SECOND_MODEL);

                assertThat(registry.get("SL").config().modelName()).isEqualTo("second");
                assertThat(reloads).hasValue(0);
            }
        }

        @Test
        @DisplayName("a refused store fires no failure listener: its caller was told instead")
        void store_whenRefused_firesNoFailureListener() {
            MemoryRow row = new MemoryRow(LAYER);
            AtomicInteger failures = new AtomicInteger();

            try (LlmRegistry registry = registryOver(row)) {
                registry.onReloadFailure(failure -> failures.incrementAndGet());

                assertThatThrownBy(() -> registry.store(row,
                        LAYER.replace("fake-local", "not-a-provider")))
                        .isInstanceOf(ConfigValidationException.class);

                assertThat(failures).hasValue(0);
            }
        }

        @Test
        @DisplayName("a watcher that wakes after a store finds nothing to publish")
        void store_leavesAWakingWatcherNothingToPublish() throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);
            AtomicInteger reloads = new AtomicInteger();

            try (LlmRegistry registry = LlmRegistry.builder()
                    .sources(List.of(SECRETS, target))
                    .notifier(FileChangeNotifier.of(List.of(file), Duration.ofMillis(50)))
                    .build()) {
                registry.onReload(change -> reloads.incrementAndGet());

                registry.store(target, LAYER_WITH_SECOND_MODEL);

                // Six times the debounce, and the count has to stay at zero for the whole
                // window rather than merely be zero once at the end. A fixed sleep asserted
                // the weaker thing and was the slowest test in this class.
                await("the application's own store must raise no event")
                        .during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(5))
                        .until(() -> reloads.get() == 0);
                assertThat(registry.get("SL").config().modelName()).isEqualTo("second");
            }
        }
    }

    @Nested
    @DisplayName("a layer that uses include")
    class WithAnInclude {

        @Test
        @DisplayName("an include the new text keeps goes on working")
        void store_canKeepAnInclude() throws IOException {
            writeLayer("included.conf", "llm { SL { model-name = \"from-include\" } }");
            Path file = writeLayer("app.conf", """
                    include "included.conf"
                    llm { SL { provider = fake-local, api-key = ${secret} } }
                    """);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            try (LlmRegistry registry = registryOver(target)) {
                assertThat(registry.get("SL").config().modelName()).isEqualTo("from-include");

                registry.store(target, """
                        include "included.conf"
                        llm { SL { provider = fake-local, api-key = ${secret}
                                   temperature = 0.5 } }
                        """);

                assertThat(read(file)).contains("include \"included.conf\"");
                assertThat(registry.get("SL").config().modelName()).isEqualTo("from-include");
                assertThat(registry.get("SL").config().temperature()).contains(0.5);
            }
        }

        @Test
        @DisplayName("the include is resolved during validation, against the layer's directory")
        void store_validatesTheIncludeAgainstTheLayersDirectory() throws IOException {
            // The included file carries the provider, so a validation that did not resolve
            // the include — or resolved it in the wrong directory — would reject this store.
            writeLayer("provider.conf", "llm { SL { provider = fake-local } }");
            Path file = writeLayer("app.conf", """
                    include "provider.conf"
                    llm { SL { api-key = ${secret}, model-name = "first" } }
                    """);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            try (LlmRegistry registry = registryOver(target)) {
                registry.store(target, """
                        include "provider.conf"
                        llm { SL { api-key = ${secret}, model-name = "second" } }
                        """);

                assertThat(registry.get("SL").config().provider()).isEqualTo("fake-local");
                assertThat(registry.get("SL").config().modelName()).isEqualTo("second");
            }
        }

        @Test
        @DisplayName("dropping an include the configuration needs is refused by validation")
        void store_droppingANeededIncludeIsRefused() throws IOException {
            writeLayer("provider.conf", "llm { SL { provider = fake-local } }");
            Path file = writeLayer("app.conf", """
                    include "provider.conf"
                    llm { SL { api-key = ${secret}, model-name = "first" } }
                    """);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);
            String before = read(file);

            try (LlmRegistry registry = registryOver(target)) {
                assertThatThrownBy(() -> registry.store(target,
                        "llm { SL { api-key = ${secret}, model-name = \"second\" } }"))
                        .isInstanceOf(ConfigValidationException.class)
                        .hasMessageContaining("provider");

                assertThat(read(file)).isEqualTo(before);
            }
        }

        @Test
        @DisplayName("dropping an include the configuration does not need goes through: only "
                + "validation guards this")
        void store_droppingAnOptionalIncludeGoesThrough() throws IOException {
            writeLayer("tuning.conf", "llm { SL { temperature = 0.5 } }");
            Path file = writeLayer("app.conf", """
                    include "tuning.conf"
                    llm { SL { provider = fake-local, api-key = ${secret}
                               model-name = "first" } }
                    """);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            try (LlmRegistry registry = registryOver(target)) {
                assertThat(registry.get("SL").config().temperature()).contains(0.5);

                // The library cannot tell a deliberate removal from an accidental one, and
                // the result is still valid. The value is gone, and nothing objects.
                registry.store(target, LAYER);

                assertThat(read(file)).doesNotContain("include");
                assertThat(registry.get("SL").config().temperature()).isEmpty();
            }
        }

        @Test
        @DisplayName("an include cannot be stored through a symbolic link to another directory")
        void store_withAnIncludeThroughASymlink_isRefused() throws IOException {
            // Measured with config-1.4.9: parsing through the link resolves the include next
            // to the link, while the staged file beside the link's target resolves it next to
            // the target. Validating against one and running on the other is what is refused.
            Path real = writeLayer("data/layer.conf", """
                    include "provider.conf"
                    llm { SL { api-key = ${secret}, model-name = "first" } }
                    """);
            Path link = dir.resolve("layer.conf");
            Files.createSymbolicLink(link, real);
            // Beside the link, because that is where the include resolves when the layer is
            // read through it. The staged file would go beside the link's target instead.
            writeLayer("provider.conf", "llm { SL { provider = fake-local } }");
            WritableConfigSource target = ConfigSource.ofWritableFile(link);
            String before = read(real);

            try (LlmRegistry registry = registryOver(target)) {
                assertThatThrownBy(() -> registry.store(target, """
                        include "provider.conf"
                        llm { SL { api-key = ${secret}, model-name = "second" } }
                        """))
                        .isInstanceOf(ConfigValidationException.class)
                        .hasMessageContaining("symbolic link")
                        .hasMessageContaining("include");

                assertThat(read(real)).isEqualTo(before);
                assertThat(registry.get("SL").config().modelName()).isEqualTo("first");
            }
            assertThat(stagedFilesUnder(dir)).isEmpty();
        }

        @Test
        @DisplayName("a text with no include is stored through a symbolic link as usual")
        void store_withoutAnIncludeThroughASymlink_isAccepted() throws IOException {
            Path real = writeLayer("data/layer.conf", LAYER);
            Path link = dir.resolve("layer.conf");
            Files.createSymbolicLink(link, real);
            WritableConfigSource target = ConfigSource.ofWritableFile(link);

            try (LlmRegistry registry = registryOver(target)) {
                registry.store(target, LAYER_WITH_SECOND_MODEL);

                assertThat(registry.get("SL").config().modelName()).isEqualTo("second");
            }

            assertThat(read(real)).isEqualTo(LAYER_WITH_SECOND_MODEL);
        }
    }

    @Nested
    @DisplayName("what a store does to the file itself")
    class FileMechanics {

        @Test
        @DisplayName("it leaves the file's permissions as it found them")
        void store_keepsTheFilesPermissions() throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            Assumptions.assumeTrue(
                    Files.getFileAttributeView(file, PosixFileAttributeView.class) != null,
                    "POSIX permissions are not a concept on this filesystem");
            // A staged file is created owner-only, and a move carries that onto the target: a
            // store must not silently turn a readable configuration into an unreadable one.
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            try (LlmRegistry registry = registryOver(target)) {
                registry.store(target, LAYER_WITH_SECOND_MODEL);
            }

            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
                    .isEqualTo("rw-r--r--");
        }

        @Test
        @DisplayName("a symlinked layer stays a symlink, and its target receives the write")
        void store_writesThroughASymlink() throws IOException {
            Path data = writeLayer("data.conf", LAYER);
            Path link = dir.resolve("link.conf");
            Files.createSymbolicLink(link, data);
            WritableConfigSource target = ConfigSource.ofWritableFile(link);

            try (LlmRegistry registry = registryOver(target)) {
                registry.store(target, LAYER_WITH_SECOND_MODEL);
            }

            // Replacing the link with an ordinary file would destroy the arrangement ADR-0024
            // exists for, and leave the data it pointed at holding the old content.
            assertThat(Files.isSymbolicLink(link)).as("the link must stay a link").isTrue();
            assertThat(read(data)).isEqualTo(LAYER_WITH_SECOND_MODEL);
        }

        @Test
        @DisplayName("a read-only file in a writable directory is still stored")
        void store_ontoAReadOnlyFile_succeedsBecauseTheDirectoryIsWhatIsWritten()
                throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            Assumptions.assumeTrue(
                    Files.getFileAttributeView(file, PosixFileAttributeView.class) != null,
                    "POSIX permissions are not a concept on this filesystem");
            Assumptions.assumeFalse("root".equals(System.getProperty("user.name")),
                    "root ignores the permissions this test relies on");
            // Staging writes beside the target and the commit renames onto it, so what a
            // store needs is write permission on the directory. Taking it away from the file
            // changes nothing. This is asserted rather than left implicit because
            // ConfigSource.ofWritableFile now tells a reader exactly this.
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            try (LlmRegistry registry = registryOver(target)) {
                registry.store(target, LAYER_WITH_SECOND_MODEL);

                assertThat(registry.get("SL").config().modelName()).isEqualTo("second");
            }

            assertThat(read(file)).isEqualTo(LAYER_WITH_SECOND_MODEL);
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
                    .as("and the file it replaced was read-only, so the new one is too")
                    .isEqualTo("r--r--r--");
        }

        @Test
        @DisplayName("a successful store leaves no staged file behind")
        void store_leavesNoStagedFile() throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            try (LlmRegistry registry = registryOver(target)) {
                registry.store(target, LAYER_WITH_SECOND_MODEL);
            }

            assertThat(stagedFilesUnder(dir)).isEmpty();
        }
    }

    @Nested
    @DisplayName("two writers at once")
    class Concurrency {

        @Test
        @DisplayName("concurrent stores are serialised, and what is live matches what is stored")
        void store_serialisesConcurrentCalls() throws Exception {
            int rounds = 50;
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                for (int round = 0; round < rounds; round++) {
                    MemoryRow row = new MemoryRow(LAYER);
                    try (LlmRegistry registry = registryOver(row)) {
                        CountDownLatch go = new CountDownLatch(1);
                        Future<?> first = pool.submit(() -> {
                            go.await();
                            return registry.store(row, LAYER.replace("\"first\"", "\"a\""));
                        });
                        Future<?> second = pool.submit(() -> {
                            go.await();
                            return registry.store(row, LAYER.replace("\"first\"", "\"b\""));
                        });
                        go.countDown();
                        first.get(10, TimeUnit.SECONDS);
                        second.get(10, TimeUnit.SECONDS);

                        String winner = registry.get("SL").config().modelName();
                        assertThat(winner).as("round %d", round).isIn("a", "b");
                        assertThat(row.text())
                                .as("round %d stored something other than what is live", round)
                                .contains("\"" + winner + "\"");
                        assertThat(row.writes).as("round %d", round).hasValue(2);
                    }
                }
            } finally {
                pool.shutdown();
                if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            }
        }

        @Test
        @DisplayName("reading a layer and storing it back is two calls, and the caller owns the "
                + "gap between them")
        void store_doesNotHoldAReadModifyWriteTogether() {
            MemoryRow row = new MemoryRow(LAYER);

            try (LlmRegistry registry = registryOver(row)) {
                // One writer reads the layer, meaning to change the model name.
                String readByFirstWriter = row.text();

                // Another writer changes something else in the meantime, and is stored.
                registry.store(row, LAYER.replace("model-name  = \"first\"",
                        "model-name  = \"first\"\n    log-requests = true"));
                assertThat(registry.get("SL").config().logRequests()).isTrue();

                // The first writer now stores what it based on the text it read.
                registry.store(row, readByFirstWriter.replace("\"first\"", "\"second\""));

                // The second writer's change is gone. store() serialises the two stores but
                // cannot see that the first one was based on a text that had moved on; that
                // is what storeIfUnchanged is for, in CompareAndSet below.
                assertThat(registry.get("SL").config().modelName()).isEqualTo("second");
                assertThat(registry.get("SL").config().logRequests()).isFalse();
                assertThat(row.text()).doesNotContain("log-requests");
            }
        }
    }

    @Nested
    @DisplayName("storing only if the layer has not moved")
    class CompareAndSet {

        @Test
        @DisplayName("it stores when the layer still holds the text the change was based on")
        void storeIfUnchanged_whenTheLayerIsUnchanged_stores() {
            MemoryRow row = new MemoryRow(LAYER);

            try (LlmRegistry registry = registryOver(row)) {
                Optional<ReloadChange> change =
                        registry.storeIfUnchanged(row, row.text(), LAYER_WITH_SECOND_MODEL);

                assertThat(change).isPresent();
                assertThat(change.get().updated()).containsExactly("SL");
                assertThat(row.text()).isEqualTo(LAYER_WITH_SECOND_MODEL);
                assertThat(registry.get("SL").config().modelName()).isEqualTo("second");
            }
        }

        @Test
        @DisplayName("it is refused when somebody else stored the layer in between")
        void storeIfUnchanged_whenTheLayerMoved_isRefused() {
            MemoryRow row = new MemoryRow(LAYER);

            try (LlmRegistry registry = registryOver(row)) {
                String base = row.text();
                String otherWriter = LAYER.replace("\"first\"", "\"from-somebody-else\"");
                registry.store(row, otherWriter);

                assertThatThrownBy(() ->
                        registry.storeIfUnchanged(row, base, LAYER_WITH_SECOND_MODEL))
                        .isInstanceOf(StaleLayerException.class)
                        .hasMessageContaining("has changed");

                assertThat(row.text()).as("the other writer's change survives")
                        .isEqualTo(otherWriter);
                assertThat(registry.get("SL").config().modelName())
                        .isEqualTo("from-somebody-else");
                assertThat(row.writes).hasValue(1);
            }
        }

        @Test
        @DisplayName("the refusal carries the text to rebase onto, and the retry succeeds")
        void storeIfUnchanged_tellsTheCallerWhatTheLayerHoldsNow() {
            MemoryRow row = new MemoryRow(LAYER);

            try (LlmRegistry registry = registryOver(row)) {
                String base = row.text();
                String otherWriter = LAYER + "llm.SL.log-requests = true\n";
                registry.store(row, otherWriter);

                StaleLayerException stale = catchThrowableOfType(
                        StaleLayerException.class,
                        () -> registry.storeIfUnchanged(row, base, LAYER_WITH_SECOND_MODEL));

                assertThat(stale.layerId()).isEqualTo(row.id());
                assertThat(stale.current()).isEqualTo(otherWriter);

                // Rebasing the same change onto what the layer actually holds now works, and
                // keeps the other writer's line.
                registry.storeIfUnchanged(row, stale.current(),
                        stale.current().replace("\"first\"", "\"second\""));

                assertThat(registry.get("SL").config().modelName()).isEqualTo("second");
                assertThat(registry.get("SL").config().logRequests()).isTrue();
            }
        }

        @Test
        @DisplayName("it compares the text exactly: a layer somebody reformatted has moved")
        void storeIfUnchanged_comparesTheTextExactly() {
            MemoryRow row = new MemoryRow(LAYER);

            try (LlmRegistry registry = registryOver(row)) {
                // Same meaning, different characters — a comment a person added on purpose.
                String base = "# an older comment\n" + LAYER;

                assertThatThrownBy(() ->
                        registry.storeIfUnchanged(row, base, LAYER_WITH_SECOND_MODEL))
                        .isInstanceOf(StaleLayerException.class);

                assertThat(row.writes).hasValue(0);
            }
        }

        @Test
        @DisplayName("an expected text that lost its final newline is refused, and says why")
        void storeIfUnchanged_withoutTheFinalNewline_saysWhatTheComparisonIncludes()
                throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            try (LlmRegistry registry = registryOver(target)) {
                // What a shell $(cat app.conf) hands back, and what an HTTP client trimming a
                // response body hands back: the same configuration, one byte shorter. Nobody
                // wrote the layer, so the message has to explain a refusal that otherwise
                // looks like a race with nobody in it.
                String trimmed = target.text().stripTrailing();
                assertThat(trimmed).isNotEqualTo(target.text());

                assertThatThrownBy(() ->
                        registry.storeIfUnchanged(target, trimmed, LAYER_WITH_SECOND_MODEL))
                        .isInstanceOf(StaleLayerException.class)
                        .hasMessageContaining("includes the final newline");

                assertThat(read(file)).isEqualTo(LAYER);
            }
        }

        @Test
        @DisplayName("it sees a file somebody edited on disk behind the application's back")
        void storeIfUnchanged_seesAFileChangedOnDisk() throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            try (LlmRegistry registry = registryOver(target)) {
                String base = target.text();
                String byHand = LAYER.replace("\"first\"", "\"edited-by-hand\"");
                Files.writeString(file, byHand, StandardCharsets.UTF_8);

                StaleLayerException stale = catchThrowableOfType(
                        StaleLayerException.class,
                        () -> registry.storeIfUnchanged(target, base, LAYER_WITH_SECOND_MODEL));

                assertThat(stale.current()).isEqualTo(byHand);
                assertThat(read(file)).isEqualTo(byHand);
            }
            assertThat(stagedFilesUnder(dir)).isEmpty();
        }

        @Test
        @DisplayName("an unchanged layer does not excuse an invalid text")
        void storeIfUnchanged_stillValidates() throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);
            String before = read(file);

            try (LlmRegistry registry = registryOver(target)) {
                assertThatThrownBy(() -> registry.storeIfUnchanged(target, target.text(),
                        LAYER.replace("fake-local", "not-a-provider")))
                        .isInstanceOf(ConfigValidationException.class)
                        .hasMessageContaining("not-a-provider");

                assertThat(read(file)).isEqualTo(before);
                assertThat(registry.get("SL").config().provider()).isEqualTo("fake-local");
            }
        }

        @Test
        @DisplayName("a write that fails puts the previous configuration back")
        void storeIfUnchanged_whenTheWriteFails_rollsBack() {
            MemoryRow row = new MemoryRow(LAYER);

            try (LlmRegistry registry = registryOver(row)) {
                row.refuseWrites = true;

                assertThatThrownBy(() ->
                        registry.storeIfUnchanged(row, row.text(), LAYER_WITH_SECOND_MODEL))
                        .isInstanceOf(ConfigAccessException.class)
                        .hasMessageContaining("unreachable");

                assertThat(registry.get("SL").config().modelName()).isEqualTo("first");
                assertThat(row.text()).isEqualTo(LAYER);
            }
        }

        @Test
        @DisplayName("it fires no reload listener either")
        void storeIfUnchanged_firesNoReloadListener() {
            MemoryRow row = new MemoryRow(LAYER);
            AtomicInteger reloads = new AtomicInteger();

            try (LlmRegistry registry = registryOver(row)) {
                registry.onReload(change -> reloads.incrementAndGet());

                registry.storeIfUnchanged(row, row.text(), LAYER_WITH_SECOND_MODEL);

                assertThat(reloads).hasValue(0);
            }
        }

        @Test
        @DisplayName("a layer this registry does not read cannot be stored through it")
        void storeIfUnchanged_withAForeignLayer_isRefused() {
            MemoryRow mine = new MemoryRow(LAYER);
            MemoryRow theirs = new MemoryRow(LAYER);

            try (LlmRegistry registry = registryOver(mine)) {
                assertThatThrownBy(() ->
                        registry.storeIfUnchanged(theirs, LAYER, LAYER_WITH_SECOND_MODEL))
                        .isInstanceOf(ConfigValidationException.class)
                        .hasMessageContaining("not one of");

                assertThat(theirs.writes).hasValue(0);
            }
        }

        @Test
        @DisplayName("none of the three arguments may be null")
        void storeIfUnchanged_withNullArguments_throws() {
            MemoryRow row = new MemoryRow(LAYER);

            try (LlmRegistry registry = registryOver(row)) {
                assertThatNullPointerException()
                        .isThrownBy(() -> registry.storeIfUnchanged(null, LAYER, LAYER));
                assertThatNullPointerException()
                        .isThrownBy(() -> registry.storeIfUnchanged(row, null, LAYER));
                assertThatNullPointerException()
                        .isThrownBy(() -> registry.storeIfUnchanged(row, LAYER, null));
                assertThat(row.writes).hasValue(0);
            }
        }

        @Test
        @DisplayName("two writers retrying against each other lose nothing")
        void storeIfUnchanged_underTwoWritersLosesNothing() throws Exception {
            // The counterpart of store_doesNotHoldAReadModifyWriteTogether: the same two
            // writers, each appending its own line, keep both changes when the store is
            // conditional and the caller retries on the text it is given back.
            int rounds = 25;
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                for (int round = 0; round < rounds; round++) {
                    MemoryRow row = new MemoryRow(LAYER);
                    try (LlmRegistry registry = registryOver(row)) {
                        CountDownLatch go = new CountDownLatch(1);
                        Future<?> first = pool.submit(() -> {
                            go.await();
                            return appendWithRetry(registry, row, "llm.SL.temperature = 0.1\n");
                        });
                        Future<?> second = pool.submit(() -> {
                            go.await();
                            return appendWithRetry(registry, row, "llm.SL.log-requests = true\n");
                        });
                        go.countDown();
                        first.get(10, TimeUnit.SECONDS);
                        second.get(10, TimeUnit.SECONDS);

                        assertThat(row.text())
                                .as("round %d lost a change", round)
                                .contains("temperature")
                                .contains("log-requests");
                        assertThat(registry.get("SL").config().temperature()).contains(0.1);
                        assertThat(registry.get("SL").config().logRequests()).isTrue();
                    }
                }
            } finally {
                pool.shutdown();
                if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            }
        }

        /**
         * Appends a line to the layer, rebasing and retrying for as long as it is refused.
         *
         * @param line the line to append
         * @return the line, once it is stored
         * @throws AssertionError if it is still being refused after {@link #MAX_ATTEMPTS} —
         *     with two writers that is a defect, and failing beats spinning until the pool's
         *     shutdown times out with nothing to say
         */
        private String appendWithRetry(LlmRegistry registry, MemoryRow row, String line) {
            String base = row.text();
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    registry.storeIfUnchanged(row, base, base + line);
                    return line;
                } catch (StaleLayerException stale) {
                    base = stale.current();
                }
            }
            throw new AssertionError(
                    "still refused after " + MAX_ATTEMPTS + " attempts, appending: " + line);
        }
    }

    @Nested
    @DisplayName("writing a file layer directly, without going through the registry")
    class WritingTheLayerDirectly {

        @Test
        @DisplayName("write() replaces the file with the given text")
        void write_replacesTheFile() throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            target.write(LAYER_WITH_SECOND_MODEL);

            assertThat(read(file)).isEqualTo(LAYER_WITH_SECOND_MODEL);
            assertThat(target.text()).isEqualTo(LAYER_WITH_SECOND_MODEL);
            assertThat(stagedFilesUnder(dir)).isEmpty();
        }

        @Test
        @DisplayName("a write that cannot be moved into place throws and leaves nothing behind")
        void write_whenTheMoveFails_discardsTheStagedFile() throws IOException {
            // A non-empty directory where the layer should be: staging succeeds, and the move
            // onto it cannot. This is the path that has to clean up after itself.
            Path occupied = dir.resolve("app.conf");
            Files.createDirectory(occupied);
            Files.writeString(occupied.resolve("inside.txt"), "x", StandardCharsets.UTF_8);
            WritableConfigSource target = ConfigSource.ofWritableFile(occupied);

            assertThatThrownBy(() -> target.write(LAYER))
                    .isInstanceOf(ConfigAccessException.class)
                    .hasMessageContaining("Cannot replace");

            assertThat(stagedFilesUnder(dir)).isEmpty();
        }

        @Test
        @DisplayName("a failed move names the file the link points at, not only the link")
        void write_whenTheMoveFails_namesTheFileAWriteReplaces() throws IOException {
            // A write follows a symbolic link, so the file that failed is not the file the
            // layer was configured with. Naming only the link sends the reader to check the
            // permissions of the wrong path — in the deployment ADR-0024 exists for, where a
            // read-only target is exactly how this fails.
            Path real = Files.createDirectory(dir.resolve("real"));
            Path occupied = Files.createDirectory(real.resolve("app.conf"));
            Files.writeString(occupied.resolve("inside.txt"), "x", StandardCharsets.UTF_8);
            Path link = dir.resolve("link.conf");
            Files.createSymbolicLink(link, occupied);
            WritableConfigSource target = ConfigSource.ofWritableFile(link);

            assertThatThrownBy(() -> target.write(LAYER))
                    .isInstanceOf(ConfigAccessException.class)
                    .hasMessageContaining("Cannot replace the configuration file " + link)
                    .hasMessageContaining("(resolved to " + link.toRealPath());

            assertThat(stagedFilesUnder(dir)).isEmpty();
        }

        @Test
        @DisplayName("a link that cannot be followed falls back to the configured path")
        void write_throughALinkThatCannotBeResolved_writesThePathItself() throws IOException {
            // The layer's path is a symbolic link pointing at itself, so it can be seen but
            // never followed to a real file. Resolving the destination has to give up and use
            // the configured path, which is the only thing left that names anything.
            //
            // Files.exists follows links and answers false for this, so the fallback is
            // reached only because the check asks with NOFOLLOW_LINKS. Without that, this
            // test would pass through a different branch and prove nothing.
            Path loop = dir.resolve("loop.conf");
            Files.createSymbolicLink(loop, loop);
            assertThat(Files.exists(loop)).as("a looping link is not there, followed").isFalse();
            assertThat(Files.exists(loop, LinkOption.NOFOLLOW_LINKS))
                    .as("but something is at the path").isTrue();

            WritableConfigSource target = ConfigSource.ofWritableFile(loop);

            target.write(LAYER);

            // The broken link is replaced by a real file: there was no target to keep it
            // pointing at, so this is the only outcome that leaves the layer readable.
            assertThat(Files.isSymbolicLink(loop)).isFalse();
            assertThat(read(loop)).isEqualTo(LAYER);
            assertThat(target.text()).isEqualTo(LAYER);
            assertThat(stagedFilesUnder(dir)).isEmpty();
        }

        @Test
        @DisplayName("write() refuses a null text")
        void write_withNullText_throws() throws IOException {
            Path file = writeLayer("app.conf", LAYER);
            WritableConfigSource target = ConfigSource.ofWritableFile(file);

            assertThatNullPointerException().isThrownBy(() -> target.write(null));

            assertThat(read(file)).isEqualTo(LAYER);
        }

        @Test
        @DisplayName("write() accepts an include: it validates nothing, so nothing can diverge")
        void write_withAnIncludeThroughASymlink_isAccepted() throws IOException {
            // The refusal belongs to the store path, which validates a staged file in one
            // directory and then reads the layer through another. A plain write does neither.
            Path real = writeLayer("data/layer.conf", LAYER);
            Path link = dir.resolve("layer.conf");
            Files.createSymbolicLink(link, real);
            WritableConfigSource target = ConfigSource.ofWritableFile(link);
            String withInclude = "include \"tuning.conf\"\n" + LAYER;

            target.write(withInclude);

            assertThat(read(real)).isEqualTo(withInclude);
            assertThat(Files.isSymbolicLink(link)).isTrue();
        }

        @Test
        @DisplayName("a staged write lands on the destination it was prepared for, even when "
                + "the link moves in between")
        void stage_resolvesTheDestinationOnce() throws IOException {
            // A ConfigMap swaps the link whenever it likes and does not wait for our lock.
            // Resolving the destination again at commit time would write the staged text onto
            // a file it was never prepared for, with permissions copied from the old one.
            Path first = writeLayer("data/first.conf", LAYER);
            Path second = writeLayer("data/second.conf", LAYER);
            Path link = dir.resolve("layer.conf");
            Files.createSymbolicLink(link, first);
            WritableFileConfigSource target = new WritableFileConfigSource(link);

            WritableFileConfigSource.StagedFile prepared =
                    target.stage(LAYER_WITH_SECOND_MODEL);
            Files.delete(link);
            Files.createSymbolicLink(link, second);
            target.commitStaged(prepared);

            assertThat(read(first)).isEqualTo(LAYER_WITH_SECOND_MODEL);
            assertThat(read(second)).as("a file this write was never prepared for")
                    .isEqualTo(LAYER);
        }

        @Test
        @DisplayName("a staged source reads the staged file, under the target layer's id")
        void stagedFileSource_readsTheStagedFile() throws IOException {
            // What the loader sees while validating a store. It is a FileBacked source, so
            // the loader parses it through its file; text() is the rest of the contract.
            Path staged = writeLayer("staged.conf", LAYER_WITH_SECOND_MODEL);

            StagedFileSource source = new StagedFileSource("the-layer", staged);

            assertThat(source.id()).isEqualTo("the-layer");
            assertThat(source.file()).isEqualTo(staged);
            assertThat(source.text()).isEqualTo(LAYER_WITH_SECOND_MODEL);
        }
    }
}
