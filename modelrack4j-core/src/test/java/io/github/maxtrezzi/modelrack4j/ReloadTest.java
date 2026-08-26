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
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hot reload: what the watcher sees, what the diff publishes, and what a rejected snapshot
 * leaves behind.
 *
 * <p>These tests drive a real {@link java.nio.file.WatchService} against real files, because
 * the behaviours that matter here — a save arriving as CREATE, a ConfigMap swap naming no
 * file the library knows about — are properties of the filesystem, not of the code. A mock
 * would assert the assumptions instead of testing them.
 */
class ReloadTest {

    /** Short enough to keep the suite quick, still ~25x the event burst one write makes. */
    private static final Duration DEBOUNCE = Duration.ofMillis(60);

    /** Generous: a loaded CI runner is slow, and a too-tight bound is a flaky test. */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** Long enough for a reload that should not happen to have happened. */
    private static final Duration QUIET = Duration.ofMillis(400);

    @TempDir
    Path dir;

    private final AtomicInteger reloads = new AtomicInteger();
    private final List<ReloadChange> changes = new CopyOnWriteArrayList<>();
    private final List<ReloadFailure> failures = new CopyOnWriteArrayList<>();
    private LlmRegistry registry;

    @AfterEach
    void closeRegistry() {
        if (registry != null) {
            registry.close();
        }
    }

    @Test
    @DisplayName("an edited block is rebuilt and reported as updated")
    void editedBlockIsRebuilt() throws IOException {
        Path file = write("app.conf", block("SL", "first"));
        watch(file);

        write("app.conf", block("SL", "second"));

        awaitModel("SL", "second");
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).updated()).containsExactly("SL");
        assertThat(changes.get(0).added()).isEmpty();
        assertThat(changes.get(0).removed()).isEmpty();
    }

    @Test
    @DisplayName("a burst of rapid writes produces exactly one reload")
    void rapidWritesCollapseIntoOneReload() throws IOException {
        Path file = write("app.conf", block("SL", "v0"));
        watch(file);

        for (int i = 1; i <= 5; i++) {
            write("app.conf", block("SL", "v" + i));
        }

        awaitModel("SL", "v5");
        // The point of the debounce. Without it this is five reloads, four of which rebuild
        // a model object nobody ever sees.
        assertThat(reloads).hasValue(1);
    }

    @Test
    @DisplayName("a save written through a temporary file and renamed is seen, once")
    void tempFileThenRenameIsSeen() throws IOException {
        Path file = write("app.conf", block("SL", "before"));
        watch(file);

        // How editors and deployment tools actually save: the config file's own event is
        // ENTRY_CREATE, and the temp file produces three events that must be discarded.
        Path temp = write("app.conf.tmp", block("SL", "after"));
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);

        awaitModel("SL", "after");
        assertThat(reloads).hasValue(1);
    }

    @Test
    @DisplayName("a Kubernetes ConfigMap symlink swap is seen, though no event names the file")
    void configMapSymlinkSwapIsSeen() throws IOException {
        // The full three-level layout. A test that merely re-points a symlink with
        // delete-then-create does not reproduce this: the events, and the reason the
        // filename filter has to be off here, come from the atomic rename of ..data.
        Path mount = Files.createDirectory(dir.resolve("mount"));
        Path generation1 = Files.createDirectory(mount.resolve("..2026_08_23_gen1"));
        Files.writeString(generation1.resolve("app.conf"), block("SL", "gen1"),
                StandardCharsets.UTF_8);
        Files.createSymbolicLink(mount.resolve("..data"), generation1.getFileName());
        Path visible = mount.resolve("app.conf");
        Files.createSymbolicLink(visible, Path.of("..data", "app.conf"));

        watch(visible);
        assertThat(registry.get("SL").config().modelName()).isEqualTo("gen1");

        Path generation2 = Files.createDirectory(mount.resolve("..2026_08_23_gen2"));
        Files.writeString(generation2.resolve("app.conf"), block("SL", "gen2"),
                StandardCharsets.UTF_8);
        Path staged = mount.resolve("..data_tmp");
        Files.createSymbolicLink(staged, generation2.getFileName());
        Files.move(staged, mount.resolve("..data"), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);

        awaitModel("SL", "gen2");
    }

    @Test
    @DisplayName("a name added by a reload is available immediately")
    void addedNameBecomesAvailable() throws IOException {
        Path file = write("app.conf", block("SL", "m"));
        watch(file);

        write("app.conf", block("SL", "m") + "\n" + block("SH", "m"));

        await().atMost(TIMEOUT).until(() -> registry.names().contains("SH"));
        assertThat(registry.get("SH").chatModel()).isNotNull();
        assertThat(changes.get(0).added()).containsExactly("SH");
        assertThat(changes.get(0).updated()).isEmpty();
    }

    @Test
    @DisplayName("a name removed by a reload is gone, and get() says so")
    void removedNameThrows() throws IOException {
        Path file = write("app.conf", block("SL", "m") + "\n" + block("SH", "m"));
        watch(file);

        write("app.conf", block("SL", "m"));

        await().atMost(TIMEOUT).until(() -> !registry.names().contains("SH"));
        assertThatThrownBy(() -> registry.get("SH"))
                .isInstanceOf(UnknownConfigurationException.class);
        assertThat(changes.get(0).removed()).containsExactly("SH");
    }

    @Test
    @DisplayName("one invalid block swaps nothing and fires exactly one failure")
    void invalidBlockSwapsNothing() throws IOException {
        Path file = write("app.conf", block("SL", "good") + "\n" + block("SH", "good"));
        watch(file);
        LlmBundle liveBundle = registry.get("SL");

        // SL is edited correctly in the same save; ADR-0012 holds it back deliberately,
        // because half a snapshot matches no file the user ever wrote.
        write("app.conf", block("SL", "edited")
                + "\nllm { SH { provider = fake-local, api-key = \"k\" } }");

        await().atMost(TIMEOUT).until(() -> !failures.isEmpty());
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).cause())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("SH");
        assertThat(failures.get(0).configFiles()).containsExactly(file);
        assertThat(reloads).hasValue(0);
        assertThat(registry.get("SL")).isSameAs(liveBundle);
        assertThat(registry.get("SL").config().modelName()).isEqualTo("good");
    }

    @Test
    @DisplayName("an unchanged block keeps its bundle instance across a reload")
    void unchangedBundlesAreCarriedOver() throws IOException {
        Path file = write("app.conf", block("SL", "m") + "\n" + block("SH", "m"));
        watch(file);
        LlmBundle beforeReload = registry.get("SL");

        write("app.conf", block("SL", "m") + "\n" + block("SH", "changed"));

        awaitModel("SH", "changed");
        // Not merely equal: the same object. Rebuilding an untouched block would replace a
        // live model instance for no reason, and the diff exists to prevent that.
        assertThat(registry.get("SL")).isSameAs(beforeReload);
    }

    @Test
    @DisplayName("rewriting the same content publishes nothing")
    void identicalContentPublishesNothing() throws IOException {
        Path file = write("app.conf", block("SL", "m"));
        watch(file);

        write("app.conf", block("SL", "m"));

        assertNothingHappens();
    }

    @Test
    @DisplayName("an unrelated file in a watched directory does not wake the registry")
    void unrelatedFileIsFilteredOut() throws IOException {
        Path file = write("app.conf", block("SL", "m"));
        watch(file);

        write("something-else.conf", block("SL", "other"));

        assertNothingHappens();
    }

    @Test
    @DisplayName("without watching, edits are ignored")
    void watchingOffIgnoresEdits() throws IOException {
        Path file = write("app.conf", block("SL", "m"));
        registry = LlmRegistry.builder().configFiles(List.of(file)).build();
        listen();

        write("app.conf", block("SL", "changed"));

        assertNothingHappens();
        assertThat(registry.get("SL").config().modelName()).isEqualTo("m");
    }

    @Test
    @DisplayName("close stops reloading")
    void closeStopsReloading() throws IOException {
        Path file = write("app.conf", block("SL", "m"));
        watch(file);
        registry.close();

        write("app.conf", block("SL", "changed"));

        assertNothingHappens();
        assertThat(registry.get("SL").config().modelName()).isEqualTo("m");
    }

    @Test
    @DisplayName("a watched directory that is deleted and recreated is picked up again")
    void lostDirectoryIsReregistered() throws IOException {
        Path nested = Files.createDirectory(dir.resolve("conf.d"));
        Path file = nested.resolve("app.conf");
        Files.writeString(file, block("SL", "before"), StandardCharsets.UTF_8);
        watch(file);

        Files.delete(file);
        Files.delete(nested);
        Files.createDirectory(nested);
        Files.writeString(file, block("SL", "middle"), StandardCharsets.UTF_8);
        awaitModel("SL", "middle");

        // That first reload proves nothing on its own: the deletion's own event is enough
        // to trigger it, and by the time the debounce expires the replacement file is
        // already there. The registration, however, died with the old directory — so this
        // second edit is only ever seen if the watcher re-registered on the new one.
        Files.writeString(file, block("SL", "after"), StandardCharsets.UTF_8);

        awaitModel("SL", "after");
    }

    @Test
    @DisplayName("a listener that throws neither breaks the reload nor stops later ones")
    void throwingListenerIsContained() throws IOException {
        Path file = write("app.conf", block("SL", "one"));
        watch(file);
        registry.onReload(change -> {
            throw new IllegalStateException("listener is broken");
        });

        write("app.conf", block("SL", "two"));
        awaitModel("SL", "two");

        write("app.conf", block("SL", "three"));
        awaitModel("SL", "three");
        assertThat(reloads).hasValue(2);
    }

    @Test
    @DisplayName("a listener may close the registry it was called from")
    void listenerCanCloseTheRegistry() throws IOException {
        Path file = write("app.conf", block("SL", "one"));
        watch(file);
        AtomicLong closeMillis = new AtomicLong(-1);
        registry.onReload(change -> {
            long start = System.nanoTime();
            registry.close();
            closeMillis.set((System.nanoTime() - start) / 1_000_000);
        });

        write("app.conf", block("SL", "two"));

        await().atMost(TIMEOUT).until(() -> closeMillis.get() >= 0);
        // Closing from inside a listener is the watcher thread asking to join itself. It
        // has to return at once rather than sit out the join timeout.
        assertThat(closeMillis.get()).isLessThan(1_000);
        assertThat(registry.get("SL").config().modelName()).isEqualTo("two");

        write("app.conf", block("SL", "three"));
        await().during(QUIET).atMost(QUIET.plusSeconds(2)).until(() -> reloads.get() == 1);
    }

    @Test
    @DisplayName("get() during a reload returns a whole bundle, never a torn one")
    void getIsSafeDuringReload() throws Exception {
        Path file = write("app.conf", block("SL", "m0"));
        watch(file);

        int readers = 4;
        CountDownLatch ready = new CountDownLatch(readers);
        CountDownLatch stop = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(readers);
        try {
            for (int i = 0; i < readers; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    while (stop.getCount() > 0) {
                        LlmBundle bundle = registry.get("SL");
                        // The bundle and the config it reports must belong together: a
                        // reader must never see a name bound to another block's model.
                        assertThat(bundle.config().name()).isEqualTo("SL");
                        assertThat(bundle.config().modelName()).startsWith("m");
                        assertThat(bundle.chatModel()).isNotNull();
                        reads.incrementAndGet();
                    }
                    return null;
                });
            }
            assertThat(ready.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            for (int i = 1; i <= 10; i++) {
                write("app.conf", block("SL", "m" + i));
                awaitModel("SL", "m" + i);
            }
        } finally {
            stop.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                    .isTrue();
        }
        assertThat(reads).hasValueGreaterThan(0);
    }

    @Test
    @DisplayName("a non-positive debounce is rejected at build time")
    void debounceMustBePositive() {
        assertThatThrownBy(() -> LlmRegistry.builder().debounce(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    private void watch(Path... files) {
        registry = LlmRegistry.builder()
                .configFiles(List.of(files))
                .watch(true)
                .debounce(DEBOUNCE)
                .build();
        listen();
    }

    private void listen() {
        registry.onReload(change -> {
            changes.add(change);
            reloads.incrementAndGet();
        });
        registry.onReloadFailure(failures::add);
    }

    @Test
    @DisplayName("a snapshot holds one generation still, and a reload does not move it")
    void snapshotIsImmuneToReload() throws IOException {
        Path file = write("llm.conf", twoBlocks("gen-1"));
        watch(file);

        LlmSnapshot held = registry.snapshot();
        assertThat(held.get("SL").config().modelName()).isEqualTo("gen-1");

        Files.writeString(file, twoBlocks("gen-2"), StandardCharsets.UTF_8);
        awaitModel("SL", "gen-2");

        // The registry has moved on; the snapshot has not, and both of its names agree.
        assertThat(registry.get("SL").config().modelName()).isEqualTo("gen-2");
        assertThat(held.get("SL").config().modelName()).isEqualTo("gen-1");
        assertThat(held.get("SH").config().modelName()).isEqualTo("gen-1");
    }

    @Test
    @DisplayName("every name in a snapshot comes from the same generation")
    void snapshotIsInternallyConsistent() throws IOException {
        Path file = write("llm.conf", twoBlocks("gen-1"));
        watch(file);

        // Hammer both names while reloads land underneath. Two separate registry.get()
        // calls can straddle a swap — that is why snapshot() exists — so this asserts the
        // property on the snapshot, which is where it is actually guaranteed.
        for (int generation = 2; generation <= 6; generation++) {
            Files.writeString(file, twoBlocks("gen-" + generation), StandardCharsets.UTF_8);
            awaitModel("SL", "gen-" + generation);
            for (int probe = 0; probe < 2_000; probe++) {
                LlmSnapshot snapshot = registry.snapshot();
                assertThat(snapshot.get("SL").config().modelName())
                        .isEqualTo(snapshot.get("SH").config().modelName());
            }
        }
    }

    @Test
    @DisplayName("a snapshot keeps a name that a later reload removed")
    void snapshotKeepsRemovedName() throws IOException {
        Path file = write("llm.conf", twoBlocks("gen-1"));
        watch(file);

        LlmSnapshot held = registry.snapshot();
        assertThat(held.names()).containsExactlyInAnyOrder("SL", "SH");
        assertThat(held.contains("SH")).isTrue();

        Files.writeString(file, block("SL", "gen-2"), StandardCharsets.UTF_8);
        awaitModel("SL", "gen-2");

        assertThatThrownBy(() -> registry.get("SH")).isInstanceOf(UnknownConfigurationException.class);
        assertThat(held.get("SH").config().modelName()).isEqualTo("gen-1");
    }

    @Test
    @DisplayName("an unknown name throws from a snapshot as it does from the registry")
    void snapshotRejectsUnknownName() throws IOException {
        watch(write("llm.conf", twoBlocks("gen-1")));

        LlmSnapshot snapshot = registry.snapshot();
        assertThat(snapshot.contains("CR")).isFalse();
        assertThatThrownBy(() -> snapshot.get("CR"))
                .isInstanceOf(UnknownConfigurationException.class)
                .hasMessageContaining("CR");
    }

    private static String twoBlocks(String modelName) {
        return "llm { SL { provider = fake-local, api-key = \"k\", model-name = \"" + modelName
                + "\" }\n      SH { provider = fake-local, api-key = \"k\", model-name = \""
                + modelName + "\" } }";
    }

    private void awaitModel(String name, String modelName) {
        await().atMost(TIMEOUT)
                .until(() -> registry.names().contains(name)
                        && registry.get(name).config().modelName().equals(modelName));
    }

    /** Asserts that no callback of either kind arrives, and keeps not arriving. */
    private void assertNothingHappens() {
        await().during(QUIET).atMost(QUIET.plusSeconds(2))
                .until(() -> reloads.get() == 0 && failures.isEmpty());
    }

    private Path write(String fileName, String hocon) throws IOException {
        Path file = dir.resolve(fileName);
        Files.writeString(file, hocon, StandardCharsets.UTF_8);
        return file;
    }

    private static String block(String name, String modelName) {
        return "llm { " + name + " { provider = fake-local, api-key = \"k\""
                + ", model-name = \"" + modelName + "\" } }";
    }
}
