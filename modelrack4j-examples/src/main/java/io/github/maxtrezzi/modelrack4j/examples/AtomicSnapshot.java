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
package io.github.maxtrezzi.modelrack4j.examples;

import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import io.github.maxtrezzi.modelrack4j.LlmSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Shows the guarantee that is hardest to see and easiest to lose: a reload swaps
 * <strong>every</strong> configured model at once, so nothing ever observes one of them
 * updated and another not.
 *
 * <p>Two names, {@code SL} and {@code SH}, are both tagged {@code gen-1} in their
 * {@code description}. Four threads read the pair as fast as they can while a single save
 * changes <em>both</em> blocks to {@code gen-2}.
 *
 * <p><strong>Each reader samples the pair twice, two different ways</strong>, because the
 * guarantee has an edge and this example exists to show exactly where it is:
 *
 * <ul>
 *   <li><strong>Two {@code registry.get(...)} calls.</strong> Each reads the live
 *       configuration, so a reload landing between them yields a mixed pair. Rare — one save
 *       here, so usually zero — but structurally possible, and reproducible at roughly two
 *       per million pairs when reloads land every few milliseconds.
 *   <li><strong>One {@link LlmRegistry#snapshot()}, then two lookups on it.</strong> One read
 *       of the published generation, so the pair cannot be mixed. This column is zero, and
 *       it is zero by construction rather than by luck.
 * </ul>
 *
 * <p>Why it matters: several models cooperating on one problem is the case this library was
 * built for, and a torn pair there is a correctness bug rather than a cosmetic one — one
 * model answering under the old configuration while its partner answers under the new. The
 * reload itself is atomic; taking a snapshot is how a caller inherits that atomicity across
 * more than one lookup.
 *
 * <pre>{@code
 * mvn install                                     # exec:java reads ~/.m2, not the reactor
 * mvn -q -pl modelrack4j-examples exec:java \
 *     -Dexec.mainClass=io.github.maxtrezzi.modelrack4j.examples.AtomicSnapshot
 * }</pre>
 *
 * @implNote <strong>No API key, and no cost.</strong> This reads configuration only — it
 *     never sends a request, so the credentials in the generated file are literals rather
 *     than substitutions. It is the one example that runs anywhere, for nothing.
 */
public final class AtomicSnapshot {

    private static final int READER_THREADS = 4;
    private static final Duration SAMPLE_BEFORE_EDIT = Duration.ofSeconds(1);
    private static final Duration SAMPLE_AFTER_EDIT = Duration.ofSeconds(2);
    private static final Duration DEBOUNCE = Duration.ofMillis(100);

    private AtomicSnapshot() {
    }

    /**
     * Runs the demonstration in a temporary directory and prints what the readers saw.
     *
     * @param args ignored
     * @throws IOException if the temporary configuration cannot be written
     * @throws InterruptedException if the sampling is interrupted
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        Path directory = Files.createTempDirectory("modelrack4j-atomic");
        Path config = directory.resolve("llm.conf");
        Files.writeString(config, configuration("gen-1"), StandardCharsets.UTF_8);

        System.out.println("config: " + config);
        System.out.println();
        System.out.println(configuration("gen-1"));

        try (LlmRegistry registry = LlmRegistry.builder()
                .configFiles(List.of(config))
                .watch(true)
                .debounce(DEBOUNCE)
                .build()) {

            registry.onReload(change ->
                    System.out.println("  [reloaded: updated=" + change.updated() + "]"));

            CountDownLatch stop = new CountDownLatch(1);
            List<Reader> readers = new ArrayList<>();
            ExecutorService pool = Executors.newFixedThreadPool(READER_THREADS);
            try {
                for (int i = 0; i < READER_THREADS; i++) {
                    Reader reader = new Reader(registry, stop);
                    readers.add(reader);
                    pool.submit(reader);
                }

                System.out.println("sampling SL and SH together on "
                        + READER_THREADS + " threads...");
                Thread.sleep(SAMPLE_BEFORE_EDIT.toMillis());

                System.out.println("  --- ONE save changes BOTH blocks to gen-2 ---");
                Files.writeString(config, configuration("gen-2"), StandardCharsets.UTF_8);

                Thread.sleep(SAMPLE_AFTER_EDIT.toMillis());
                stop.countDown();
            } finally {
                // ExecutorService is not AutoCloseable on Java 17.
                pool.shutdown();
                if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            }

            report(readers);
        }
    }

    /** Merges what every reader saw, in the order the pairs first appeared. */
    private static void report(List<Reader> readers) {
        Map<String, Long> totals = new LinkedHashMap<>();
        long torn = 0;
        for (Reader reader : readers) {
            for (Map.Entry<String, Long> seen : reader.observed.entrySet()) {
                totals.merge(seen.getKey(), seen.getValue(), Long::sum);
            }
            torn += reader.torn;
        }

        System.out.println();
        System.out.println("pairs observed, in the order they first appeared:");
        int n = 0;
        for (Map.Entry<String, Long> pair : totals.entrySet()) {
            System.out.printf("  %d.  %-28s %,15d samples%n", ++n, pair.getKey(), pair.getValue());
        }

        long tornSnapshot = 0;
        for (Reader reader : readers) {
            tornSnapshot += reader.tornSnapshot;
        }

        System.out.println();
        System.out.printf("torn pairs via two get() calls : %d%n", torn);
        System.out.printf("torn pairs via snapshot()      : %d%n", tornSnapshot);
        System.out.println();
        if (tornSnapshot > 0) {
            System.out.println("BUG: a snapshot tore. The swap is no longer atomic.");
        } else if (torn > 0) {
            System.out.println("As designed: two get() calls straddled a reload; the snapshot never did.");
        } else {
            System.out.println("No tear either way this run — the get() window is narrow, not absent.");
            System.out.println("Only the snapshot column is guaranteed to stay at zero.");
        }
    }

    /** Reads both names as one observation, as fast as it can, until told to stop. */
    private static final class Reader implements Runnable {

        private final LlmRegistry registry;
        private final CountDownLatch stop;
        private final Map<String, Long> observed = new LinkedHashMap<>();
        private long torn;
        private long tornSnapshot;

        Reader(LlmRegistry registry, CountDownLatch stop) {
            this.registry = registry;
            this.stop = stop;
        }

        @Override
        public void run() {
            while (stop.getCount() > 0) {
                // Two separate get() calls, deliberately: each re-reads the live
                // configuration, so a reload landing between them produces a mixed pair.
                String sl = generationOf("SL");
                String sh = generationOf("SH");
                if (!sl.equals(sh)) {
                    torn++;
                }
                observed.merge("SL=" + sl + "  SH=" + sh, 1L, Long::sum);

                // The same question asked of one generation held still. This must never tear.
                LlmSnapshot held = registry.snapshot();
                if (!generationOf(held, "SL").equals(generationOf(held, "SH"))) {
                    tornSnapshot++;
                }
            }
        }

        private String generationOf(String name) {
            return registry.get(name).config().description().orElse("(none)");
        }

        private static String generationOf(LlmSnapshot snapshot, String name) {
            return snapshot.get(name).config().description().orElse("(none)");
        }
    }

    private static String configuration(String generation) {
        // Literal keys: nothing here ever calls a provider, so no credential is needed and
        // the example costs nothing to run.
        return """
                llm {
                  SL {
                    description = "%s"
                    provider    = anthropic
                    api-key     = "unused-no-request-is-sent"
                    model-name  = "claude-sonnet-5"
                    temperature = 0.2
                  }

                  SH {
                    description = "%s"
                    provider    = anthropic
                    api-key     = "unused-no-request-is-sent"
                    model-name  = "claude-sonnet-5"
                    temperature = 0.9
                  }
                }
                """.formatted(generation, generation);
    }
}
