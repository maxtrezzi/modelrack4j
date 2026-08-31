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

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Configuration that does not come from a file, and the reload an application asks for
 * (ADR-0042).
 */
class ConfigSourceTest {

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
    @DisplayName("watch(true) without files to watch is refused, not silently ignored")
    void watchingWithoutFilesIsRefused() {
        assertThatThrownBy(() -> LlmRegistry.builder()
                .sources(List.of(ConfigSource.of("row#1", block("SL", "m"))))
                .watch(true)
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("reload()");
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
        try (LlmRegistry registry = LlmRegistry.builder().sources(List.of(slow)).build()) {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    registry.reload();
                    return null;
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(highWaterMark).hasValue(1);
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
}
