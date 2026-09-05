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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Configuration that does not come from a file, and the reload an application asks for
 * (ADR-0042).
 */
class ConfigSourceTest {

    /** Short enough to keep the suite quick, still well above the burst one write makes. */
    private static final Duration DEBOUNCE = Duration.ofMillis(60);

    /** Generous: a loaded CI runner is slow, and a too-tight bound is a flaky test. */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    private Path dir;

    private static String block(String name, String modelName) {
        return "llm { " + name + " { provider = fake-local, api-key = \"k\""
                + ", model-name = \"" + modelName + "\" } }";
    }

    /** A source standing in for a database row: its text is whatever was last stored in it. */
    private static final class MutableSource implements ConfigSource {

        private final String id;
        private final AtomicReference<String> text;
        private final AtomicInteger reads = new AtomicInteger();

        MutableSource(String id, String text) {
            this.id = id;
            this.text = new AtomicReference<>(text);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String text() {
            reads.incrementAndGet();
            return text.get();
        }

        void store(String newText) {
            text.set(newText);
        }

        int reads() {
            return reads.get();
        }
    }

    @Test
    @DisplayName("a registry can be built from configuration that never touches the disk")
    void buildsFromTextAlone() {
        try (LlmRegistry registry = LlmRegistry.builder()
                .sources(List.of(ConfigSource.of("row#1", block("SL", "m"))))
                .build()) {
            assertThat(registry.names()).containsExactly("SL");
            assertThat(registry.get("SL").config().modelName()).isEqualTo("m");
        }
    }

    @Test
    @DisplayName("sources() gives back the layers that were supplied, in the same order")
    void sourcesAreReportedInPrecedenceOrder() {
        ConfigSource base = ConfigSource.of("row#base", block("SL", "from-base"));
        ConfigSource over = ConfigSource.of("row#over", block("SL", "from-over"));

        try (LlmRegistry registry = LlmRegistry.builder().sources(List.of(base, over))
                .build()) {
            // Lowest precedence first, which is the order sources(...) documents and the
            // order the winning value proves: the last layer is the one that wins.
            assertThat(registry.sources()).containsExactly(base, over);
            assertThat(registry.get("SL").config().modelName()).isEqualTo("from-over");
        }
    }

    @Test
    @DisplayName("sources() hands back a list a caller cannot change under the registry")
    void sourcesCannotBeModified() {
        ConfigSource row = ConfigSource.of("row#1", block("SL", "m"));

        try (LlmRegistry registry = LlmRegistry.builder().sources(List.of(row)).build()) {
            List<ConfigSource> reported = registry.sources();

            assertThatThrownBy(() -> reported.add(ConfigSource.of("row#2", "")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(registry.sources()).containsExactly(row);
        }
    }

    @Test
    @DisplayName("the writable layer can be found through sources() and stored through")
    void theWritableLayerIsReachableFromTheRegistry() throws IOException {
        Path file = dir.resolve("runtime.conf");
        Files.writeString(file, block("SL", "before"), StandardCharsets.UTF_8);
        ConfigSource base = ConfigSource.of("row#base", block("SH", "fixed"));

        try (LlmRegistry registry = LlmRegistry.builder()
                .sources(List.of(base, ConfigSource.ofWritableFile(file)))
                .build()) {
            // The example in sources()'s javadoc, run: an application that did not keep the
            // reference alongside the registry can still find the layer it may write.
            WritableConfigSource writable = registry.sources().stream()
                    .filter(WritableConfigSource.class::isInstance)
                    .map(WritableConfigSource.class::cast)
                    .findFirst()
                    .orElseThrow();

            registry.store(writable, block("SL", "after"));

            assertThat(registry.get("SL").config().modelName()).isEqualTo("after");
            assertThat(registry.get("SH").config().modelName()).isEqualTo("fixed");
        }
    }

    @Test
    @DisplayName("a failure listener is handed the same layers sources() reports")
    void aFailureReportsTheSameLayers() {
        MutableSource row = new MutableSource("row#1", block("SL", "m"));
        List<ReloadFailure> failures = new ArrayList<>();

        try (LlmRegistry registry = LlmRegistry.builder().sources(List.of(row)).build()) {
            registry.onReloadFailure(failures::add);
            row.store("llm { SL { provider = nope } }");

            assertThatThrownBy(registry::reload).isInstanceOf(ConfigValidationException.class);

            // ReloadFailure carried the layers before sources() existed, on the one path an
            // application is least likely to have written. The two must not drift apart.
            assertThat(failures).singleElement()
                    .extracting(ReloadFailure::sources)
                    .isEqualTo(registry.sources());
        }
    }

    @Test
    @DisplayName("reload() publishes what the source now says, and reports what changed")
    void manualReloadPublishesTheNewText() {
        MutableSource row = new MutableSource("row#1", block("SL", "before"));
        try (LlmRegistry registry = LlmRegistry.builder().sources(List.of(row)).build()) {
            row.store(block("SL", "after"));

            // Nothing watches a database row, so until it is asked the registry is right to
            // still be serving the old value.
            assertThat(registry.get("SL").config().modelName()).isEqualTo("before");

            Optional<ReloadChange> change = registry.reload();

            assertThat(change).isPresent();
            assertThat(change.get().updated()).containsExactly("SL");
            assertThat(registry.get("SL").config().modelName()).isEqualTo("after");
        }
    }

    @Test
    @DisplayName("reload() re-reads the source rather than trusting what it read before")
    void manualReloadRereadsTheSource() {
        MutableSource row = new MutableSource("row#1", block("SL", "m"));
        try (LlmRegistry registry = LlmRegistry.builder().sources(List.of(row)).build()) {
            int afterBuild = row.reads();

            registry.reload();

            assertThat(row.reads()).isGreaterThan(afterBuild);
        }
    }

    @Test
    @DisplayName("reload() reports nothing when the configuration did not actually change")
    void unchangedReloadIsEmpty() {
        MutableSource row = new MutableSource("row#1", block("SL", "m"));
        try (LlmRegistry registry = LlmRegistry.builder().sources(List.of(row)).build()) {
            LlmBundle before = registry.get("SL");

            assertThat(registry.reload()).isEmpty();
            assertThat(registry.get("SL")).isSameAs(before);
        }
    }

    @Test
    @DisplayName("a rejected manual reload throws to the caller and keeps the old snapshot")
    void rejectedManualReloadThrowsAndChangesNothing() {
        MutableSource row = new MutableSource("row#1", block("SL", "good"));
        try (LlmRegistry registry = LlmRegistry.builder().sources(List.of(row)).build()) {
            LlmBundle live = registry.get("SL");
            row.store("llm { SL { provider = nope-not-a-provider, api-key = \"k\""
                    + ", model-name = \"m\" } }");

            assertThatThrownBy(registry::reload)
                    .isInstanceOf(ConfigValidationException.class)
                    .hasMessageContaining("nope-not-a-provider");

            assertThat(registry.get("SL")).isSameAs(live);
            assertThat(registry.get("SL").config().modelName()).isEqualTo("good");
        }
    }

    @Test
    @DisplayName("a rejected manual reload reaches the failure listeners as well as the caller")
    void rejectedManualReloadAlsoNotifiesListeners() {
        MutableSource row = new MutableSource("row#1", block("SL", "good"));
        try (LlmRegistry registry = LlmRegistry.builder().sources(List.of(row)).build()) {
            AtomicReference<ReloadFailure> seen = new AtomicReference<>();
            registry.onReloadFailure(seen::set);
            row.store("llm { SL { provider = fake-local } }");   // no api-key

            assertThatThrownBy(registry::reload).isInstanceOf(ConfigValidationException.class);

            assertThat(seen.get()).isNotNull();
            assertThat(seen.get().sources()).extracting(ConfigSource::id).containsExactly("row#1");
        }
    }

    @Test
    @DisplayName("the source's id, not a file name, is what a parse error names")
    void parseErrorsNameTheSource() {
        assertThatThrownBy(() -> LlmRegistry.builder()
                .sources(List.of(ConfigSource.of("llm_config#42", "llm { SL { =")))
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("llm_config#42");
    }

    @Test
    @DisplayName("a file layer still resolves an include relative to itself")
    void aFileLayerResolvesItsIncludes() throws IOException {
        Files.writeString(dir.resolve("extra.conf"), block("SH", "included"),
                StandardCharsets.UTF_8);
        Path base = dir.resolve("base.conf");
        Files.writeString(base, "include \"extra.conf\"\n" + block("SL", "own"),
                StandardCharsets.UTF_8);

        try (LlmRegistry registry =
                LlmRegistry.builder().configFiles(List.of(base)).build()) {
            // Reading the file and parsing the text loses this: the includer then looks on
            // the classpath, finds nothing, and drops SH in silence because an include is
            // allow-missing.
            assertThat(registry.names()).containsExactly("SH", "SL");
            assertThat(registry.get("SH").config().modelName()).isEqualTo("included");
        }
    }

    @Test
    @DisplayName("watch(true) with no file among the layers is refused, and says which they are")
    void watchingWithoutFilesIsRefused() {
        assertThatThrownBy(() -> LlmRegistry.builder()
                .sources(List.of(ConfigSource.of("row#1", block("SL", "m"))))
                .watch(true)
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("row#1")
                .hasMessageContaining("reload()");
    }

    @Test
    @DisplayName("watch(true) watches a file layer given through sources(...)")
    void watchingAFileGivenThroughSources() throws IOException {
        Path file = dir.resolve("app.conf");
        Files.writeString(file, block("SL", "first"), StandardCharsets.UTF_8);

        // Every layer here is a file, and this was refused for having none (ADR-0050).
        try (LlmRegistry registry = LlmRegistry.builder()
                .sources(List.of(ConfigSource.ofFile(file)))
                .watch(true)
                .debounce(DEBOUNCE)
                .build()) {
            Files.writeString(file, block("SL", "second"), StandardCharsets.UTF_8);

            awaitModel(registry, "SL", "second");
        }
    }

    @Test
    @DisplayName("a registry mixing a file and a row is watched over its file half")
    void watchingAMixedRegistryWatchesTheFile() throws IOException {
        Path file = dir.resolve("base.conf");
        Files.writeString(file, block("SL", "first"), StandardCharsets.UTF_8);
        MutableSource row = new MutableSource("row#1", block("SH", "unwatched"));

        try (LlmRegistry registry = LlmRegistry.builder()
                .sources(List.of(ConfigSource.ofFile(file), row))
                .watch(true)
                .debounce(DEBOUNCE)
                .build()) {
            Files.writeString(file, block("SL", "second"), StandardCharsets.UTF_8);

            awaitModel(registry, "SL", "second");
            // The row is not watched and nothing pretends otherwise: it changed, and only
            // the file's event carried the change into the registry.
            assertThat(registry.get("SH").config().modelName()).isEqualTo("unwatched");
        }
    }

    @Test
    @DisplayName("one registry can store a layer and pick up a hand edit of another")
    void storingAndWatchingInOneRegistry() throws IOException {
        Path base = dir.resolve("base.conf");
        Path user = dir.resolve("user.conf");
        Files.writeString(base, block("SL", "first"), StandardCharsets.UTF_8);
        Files.writeString(user, block("SH", "first"), StandardCharsets.UTF_8);
        WritableConfigSource target = ConfigSource.ofWritableFile(user);

        try (LlmRegistry registry = LlmRegistry.builder()
                .sources(List.of(ConfigSource.ofFile(base), target))
                .watch(true)
                .debounce(DEBOUNCE)
                .build()) {
            registry.store(target, block("SH", "stored"));
            assertThat(registry.get("SH").config().modelName()).isEqualTo("stored");

            Files.writeString(base, block("SL", "edited"), StandardCharsets.UTF_8);

            awaitModel(registry, "SL", "edited");
            assertThat(registry.get("SH").config().modelName()).isEqualTo("stored");
        }
    }

    @Test
    @DisplayName("watch(true) and a supplied notifier together are refused as ambiguous")
    void watchAndNotifierTogetherAreRefused() {
        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(Path.of("unused.conf")))
                .watch(true)
                .notifier(new NoopNotifier())
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("not both");
    }

    @Test
    @DisplayName("two sources sharing an id are refused, because the id has to identify one")
    void duplicateIdsAreRefused() {
        assertThatThrownBy(() -> LlmRegistry.builder()
                .sources(List.of(
                        ConfigSource.of("same", block("SL", "m")),
                        ConfigSource.of("same", block("SH", "m"))))
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("same");
    }

    @Test
    @DisplayName("a file source reads its file as UTF-8, every time it is asked")
    void aFileSourceReadsItsFile() throws IOException {
        Path file = dir.resolve("app.conf");
        Files.writeString(file, "# città, naïve\n" + block("SL", "m"), StandardCharsets.UTF_8);
        ConfigSource source = ConfigSource.ofFile(file);

        assertThat(source.text()).contains("città, naïve").contains("SL");

        Files.writeString(file, block("SL", "edited"), StandardCharsets.UTF_8);
        assertThat(source.text()).contains("edited");
    }

    @Test
    @DisplayName("a file source that cannot read names the file and the reason")
    void aFileSourceThatCannotReadSaysSo() {
        ConfigSource source = ConfigSource.ofFile(dir.resolve("absent.conf"));

        assertThatThrownBy(source::text)
                .isInstanceOf(ConfigAccessException.class)
                .isNotInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("absent.conf")
                .hasMessageContaining("does not exist or is not readable");
    }

    @Test
    @DisplayName("a layer that cannot be read fails the build as access, not as validation")
    void aLayerThatCannotBeReadFailsTheBuild() {
        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(dir.resolve("absent.conf")))
                .build())
                .isInstanceOf(ConfigAccessException.class)
                // Kept apart on purpose: an application answering an HTTP request has to tell
                // "your text is wrong" from "the medium failed", and a subclass would not let
                // it. This assertion is what stops the two being merged later.
                .isNotInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("absent.conf");
    }

    @Test
    @DisplayName("a layer that becomes unreadable is reported as access and changes nothing")
    void aLayerThatBecomesUnreadableIsReportedAsAccess() throws IOException {
        Path file = dir.resolve("app.conf");
        Files.writeString(file, block("SL", "first"), StandardCharsets.UTF_8);
        List<ReloadFailure> failures = new ArrayList<>();

        try (LlmRegistry registry =
                LlmRegistry.builder().configFiles(List.of(file)).build()) {
            registry.onReloadFailure(failures::add);
            Files.delete(file);

            assertThatThrownBy(registry::reload)
                    .isInstanceOf(ConfigAccessException.class)
                    .isNotInstanceOf(ConfigValidationException.class);

            assertThat(failures).hasSize(1);
            assertThat(failures.get(0).cause()).isInstanceOf(ConfigAccessException.class);
            assertThat(registry.get("SL").config().modelName()).isEqualTo("first");
        }
    }

    @Test
    @DisplayName("two spellings of one file are the duplicate layer they are")
    void twoSpellingsOfOneFileAreOneLayer() throws IOException {
        Path file = dir.resolve("app.conf");
        Files.writeString(file, block("SL", "m"), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file, dir.resolve(".").resolve("app.conf")))
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("distinct ids");
    }

    @Test
    @DisplayName("a bad layer closes the notifier the caller had handed over")
    void aBadLayerClosesTheSuppliedNotifier() {
        NoopNotifier notifier = new NoopNotifier();

        assertThatThrownBy(() -> LlmRegistry.builder()
                .sources(List.of(ConfigSource.of("row#1", "llm { SL { provider = fake-local } }")))
                .notifier(notifier)
                .build())
                .isInstanceOf(ConfigValidationException.class);

        // build() threw, so the caller never got the registry that owns it.
        assertThat(notifier.closed).isTrue();
    }

    @Test
    @DisplayName("a supplied notifier drives the reload, and is closed with the registry")
    void aSuppliedNotifierDrivesTheReload() {
        MutableSource row = new MutableSource("row#1", block("SL", "before"));
        NoopNotifier notifier = new NoopNotifier();
        try (LlmRegistry registry =
                LlmRegistry.builder().sources(List.of(row)).notifier(notifier).build()) {
            row.store(block("SL", "after"));

            notifier.fire();   // what a database LISTEN/NOTIFY would do

            assertThat(registry.get("SL").config().modelName()).isEqualTo("after");
        }
        assertThat(notifier.closed).isTrue();
    }

    @Test
    @DisplayName("a notifier's failing reload is swallowed, logged and reported, never thrown")
    void aNotifiersFailingReloadDoesNotEscape() {
        MutableSource row = new MutableSource("row#1", block("SL", "good"));
        NoopNotifier notifier = new NoopNotifier();
        AtomicInteger failures = new AtomicInteger();
        try (LlmRegistry registry =
                LlmRegistry.builder().sources(List.of(row)).notifier(notifier).build()) {
            registry.onReloadFailure(f -> failures.incrementAndGet());
            row.store("llm { SL { provider = fake-local } }");   // no api-key

            notifier.fire();   // must not throw into the notifier's thread

            assertThat(failures).hasValue(1);
            assertThat(registry.get("SL").config().modelName()).isEqualTo("good");
        }
    }

    /**
     * Two threads reloading at once must not overlap.
     *
     * <p>Without the registry's lock both read the same live snapshot, both build, and the
     * later write discards the earlier one — after its listeners have already announced it.
     * The source below reports the highest number of readers it ever saw at one time, and
     * sleeps long enough that unsynchronised reloads would certainly overlap. Removing the
     * {@code synchronized} in {@code LlmRegistry.reload()} makes this fail.
     */
    @Test
    @DisplayName("concurrent reloads run one at a time")
    void concurrentReloadsAreSerialised() throws InterruptedException {
        int threads = 4;
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger highWaterMark = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);

        // A fresh text each time, so no reload can exit early on an empty diff and skip the
        // window this test is trying to open.
        AtomicInteger generation = new AtomicInteger();
        ConfigSource slow = new ConfigSource() {
            @Override
            public String id() {
                return "slow";
            }

            @Override
            public String text() {
                highWaterMark.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                try {
                    Thread.sleep(50);
                    return block("SL", "m" + generation.incrementAndGet());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                } finally {
                    inFlight.decrementAndGet();
                }
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> running = new ArrayList<>(threads);
        try (LlmRegistry registry = LlmRegistry.builder().sources(List.of(slow)).build()) {
            for (int i = 0; i < threads; i++) {
                running.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    registry.reload();
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        // A submitted task's exception lands in its Future and nowhere else, so without this
        // a reload that threw would leave the high-water mark at 1 and the test green.
        for (Future<?> f : running) {
            assertThatCode(f::get).doesNotThrowAnyException();
        }
        assertThat(highWaterMark).hasValue(1);
    }

    @Test
    @DisplayName("a file notifier starts once, and a closed one is spent rather than reusable")
    void aFileNotifierStartsOnce() {
        FileChangeNotifier notifier =
                FileChangeNotifier.of(List.of(dir.resolve("app.conf")), Duration.ofMillis(50));
        try {
            notifier.start(() -> { });

            assertThatThrownBy(() -> notifier.start(() -> { }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("STARTED");
        } finally {
            notifier.close();
        }

        assertThatThrownBy(() -> notifier.start(() -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED");
    }

    @Test
    @DisplayName("a file notifier rejects a debounce the builder would also reject, the same way")
    void aFileNotifierRejectsANonPositiveDebounce() {
        List<Path> file = List.of(dir.resolve("app.conf"));

        // The same invalid value reaches this class through two public doors. Both are a
        // programming error rather than a bad configuration, so both throw the same type.
        assertThatThrownBy(() -> FileChangeNotifier.of(file, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("debounce must be positive");
        assertThatThrownBy(() -> FileChangeNotifier.of(file, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LlmRegistry.builder().debounce(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);

        // An empty list stays a ConfigValidationException: nothing to watch is a statement
        // about the configuration, not about a malformed argument.
        assertThatThrownBy(() -> FileChangeNotifier.of(List.of(), Duration.ofMillis(50)))
                .isInstanceOf(ConfigValidationException.class);
    }

    @Test
    @DisplayName("a notifier that fails to start is closed, because nobody else could")
    void aNotifierThatFailsToStartIsClosed() {
        FailingNotifier notifier = new FailingNotifier(null);

        assertThatThrownBy(() -> LlmRegistry.builder()
                .sources(List.of(ConfigSource.of("row#1", block("SL", "m"))))
                .notifier(notifier)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cannot watch");

        // build() never returns, so the caller has no registry to close it with.
        assertThat(notifier.closed).isTrue();
    }

    @Test
    @DisplayName("a close that also fails is attached to the start failure, not swallowed")
    void aFailingCloseIsSuppressedRatherThanLost() {
        FailingNotifier notifier = new FailingNotifier(new IllegalStateException("close broke"));

        assertThatThrownBy(() -> LlmRegistry.builder()
                .sources(List.of(ConfigSource.of("row#1", block("SL", "m"))))
                .notifier(notifier)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cannot watch")
                .satisfies(thrown -> assertThat(thrown.getSuppressed())
                        .singleElement()
                        .extracting(Throwable::getMessage)
                        .isEqualTo("close broke"));
    }

    /** A notifier whose {@code start} always fails, and whose {@code close} may. */
    private static final class FailingNotifier implements ChangeNotifier {

        private final RuntimeException closeFailure;
        private boolean closed;

        FailingNotifier(RuntimeException closeFailure) {
            this.closeFailure = closeFailure;
        }

        @Override
        public void start(Runnable onChange) {
            throw new IllegalStateException("cannot watch");
        }

        @Override
        public void close() {
            closed = true;
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }

    /**
     * Closing from several threads must call the notifier's {@code close()} once.
     *
     * <p>Unlike the reload lock, this one is probabilistic, because the window between
     * reading the field and clearing it is two instructions wide. The rounds are what make it
     * usable. Measured against a deliberately broken {@code close()}: a single round caught
     * the defect in 3 runs out of 5, and 40 rounds in 10 out of 11. Good enough to defend the
     * fix, not a proof — what actually makes the double close impossible is
     * {@code getAndSet}, and this test is here so that removing it is noticed.
     */
    @Test
    @DisplayName("the notifier is closed exactly once, however many threads close the registry")
    void theNotifierIsClosedExactlyOnce() throws InterruptedException {
        int threads = 4;
        int rounds = 40;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < rounds; round++) {
                AtomicInteger closes = new AtomicInteger();
                LlmRegistry registry = LlmRegistry.builder()
                        .sources(List.of(ConfigSource.of("row#1", block("SL", "m"))))
                        .notifier(new CountingNotifier(closes))
                        .build();

                CountDownLatch go = new CountDownLatch(1);
                List<Future<?>> closing = new ArrayList<>(threads);
                for (int i = 0; i < threads; i++) {
                    closing.add(pool.submit(() -> {
                        go.await();
                        registry.close();
                        return null;
                    }));
                }
                go.countDown();
                for (Future<?> f : closing) {
                    assertThatCode(f::get).doesNotThrowAnyException();
                }

                // AutoCloseable.close() is explicitly not required to be idempotent, and
                // this is a public extension point, so a second call would be outside the
                // implementer's contract rather than merely wasteful.
                assertThat(closes).hasValue(1);
            }
        } finally {
            pool.shutdownNow();
        }

        // Asserted after the try, not inside the finally: a shutdown that timed out there
        // would replace whatever the rounds above were failing for.
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    }

    /** Counts how often the registry closed it. */
    private static final class CountingNotifier implements ChangeNotifier {

        private final AtomicInteger closes;

        CountingNotifier(AtomicInteger closes) {
            this.closes = closes;
        }

        @Override
        public void start(Runnable onChange) {
            // Nothing to start: this test is about closing.
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }

    /** A notifier that does nothing until a test fires it by hand. */
    private static final class NoopNotifier implements ChangeNotifier {

        private Runnable onChange;
        private boolean closed;

        @Override
        public void start(Runnable onChange) {
            this.onChange = onChange;
        }

        void fire() {
            onChange.run();
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static void awaitModel(LlmRegistry registry, String name, String modelName) {
        await().atMost(TIMEOUT)
                .until(() -> registry.names().contains(name)
                        && registry.get(name).config().modelName().equals(modelName));
    }
}
