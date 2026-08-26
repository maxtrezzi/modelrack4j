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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds one ready-to-use {@link LlmBundle} per configured name, and keeps them current.
 *
 * <p>Build it once at startup and ask it for a bundle whenever you need one:
 *
 * <pre>{@code
 * LlmRegistry registry = LlmRegistry.builder()
 *         .configFiles(List.of(defaults, product, customer))   // lowest -> highest
 *         .watch(true)                                         // reload on edit
 *         .build();
 *
 * ChatModel model = registry.get("SL").chatModel();
 * }</pre>
 *
 * <p><strong>Ask the registry every time; do not cache the bundle.</strong> The registry is
 * the holder, and {@link #get(String)} always returns the current bundle for a name. Code
 * that fetches a bundle at startup and keeps it in a field will keep working and will never
 * see a configuration change — the single most common mistake with reloadable configuration.
 *
 * <p>Building is fail-fast and all-or-nothing: every named block is parsed, validated and
 * built before the registry exists, so a registry that was returned has no broken bundles in
 * it.
 *
 * <h2>Reload</h2>
 *
 * <p>With {@link Builder#watch(boolean) watching} enabled, edits to any configured layer are
 * picked up without a restart. A reload is atomic across the <em>whole</em> snapshot: every
 * changed block is rebuilt in a staging area, and only once all of them succeed is a single
 * reference swapped. If any block fails to parse, validate or build, nothing is swapped —
 * the previous snapshot stays live in full and {@link #onReloadFailure} fires once. There is
 * no state in which one name's new configuration is visible next to another's old one.
 *
 * <p>Unchanged blocks are not rebuilt: the diff is record equality on the parsed
 * configuration, and a name whose block did not change keeps the very bundle instance it
 * had.
 *
 * @implNote {@link #get(String)} and {@link #names()} are safe to call from any thread at
 *     any time, including during a reload. Listeners run on the watcher thread, one reload
 *     at a time; a listener that blocks delays the next reload.
 */
public final class LlmRegistry implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LlmRegistry.class);

    /** The default quiet period, ~100x the event burst measured for one write (Task 0.8). */
    private static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(300);

    private final List<Path> configFiles;
    private final SnapshotLoader loader;
    private final List<Consumer<ReloadChange>> reloadListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ReloadFailure>> failureListeners =
            new CopyOnWriteArrayList<>();

    /** The published snapshot. Reads are lock-free; the only writer is the reload thread. */
    private volatile Map<String, LlmBundle> bundles;

    /**
     * Null when watching is off, and again once closed. Volatile because {@link #close()}
     * may be called from a different thread than the one that built the registry.
     */
    private volatile ConfigWatcher watcher;

    private LlmRegistry(List<Path> configFiles, SnapshotLoader loader,
            Map<String, LlmBundle> bundles) {
        this.configFiles = configFiles;
        this.loader = loader;
        this.bundles = bundles;
    }

    /**
     * Returns a builder for a registry.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the current bundle for a name.
     *
     * <p>Each call reads the live configuration, which is what makes a reload visible without
     * a restart. It also means <strong>two consecutive calls are not guaranteed to come from
     * the same generation</strong>: a reload landing between them returns bundles built from
     * different file contents. Where several models have to agree, use {@link #snapshot()}.
     *
     * @param name the configuration name, as written in the config file
     * @return the bundle bound to that name
     * @throws UnknownConfigurationException if no bundle is bound to the name, either
     *     because it was never configured or because a reload removed it
     */
    public LlmBundle get(String name) {
        LlmBundle bundle = bundles.get(Objects.requireNonNull(name, "name"));
        if (bundle == null) {
            throw new UnknownConfigurationException(name);
        }
        return bundle;
    }

    /**
     * Returns the current generation, held still, so several lookups agree with each other.
     *
     * <p>{@link #get(String)} reads the live configuration on every call. Two consecutive
     * calls can therefore straddle a reload and return bundles built from different file
     * contents — rare, but reproducible, and a correctness hazard wherever several models
     * are expected to be consistent with one another. This method reads the published
     * generation exactly once and hands it back; everything taken from the result belongs to
     * that one generation.
     *
     * <pre>{@code
     * LlmSnapshot models = registry.snapshot();
     * var fast = models.get("SL");
     * var deep = models.get("SH");   // same generation as fast, guaranteed
     * }</pre>
     *
     * <p>The returned snapshot never updates. Take one per unit of work — per request, per
     * council round — and let it go. Holding one for the lifetime of the application is the
     * caching trap in a new costume.
     *
     * @return the current generation
     */
    public LlmSnapshot snapshot() {
        return new LlmSnapshot(bundles);
    }

    /**
     * Returns every configured name, in sorted order.
     *
     * @return an unmodifiable snapshot of the names currently held
     */
    public Set<String> names() {
        return Collections.unmodifiableSet(bundles.keySet());
    }

    /**
     * Registers a listener called once per successful reload.
     *
     * <p>The listener is secondary: {@link #get(String)} already returns the current bundle,
     * so nothing has to be re-fetched or swapped in response. Use this to log the change, to
     * warm a cache, or to react to a name appearing or disappearing.
     *
     * <p>Listeners run on the watcher thread after the swap, so {@link #get(String)} inside
     * one already sees the new snapshot. An exception thrown by a listener is logged and
     * does not affect the reload, the other listeners, or later reloads.
     *
     * @param listener called with what the reload changed
     * @throws NullPointerException if the listener is null
     */
    public void onReload(Consumer<ReloadChange> listener) {
        reloadListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Registers a listener called once per rejected reload.
     *
     * <p>A rejected reload changes nothing: the previous snapshot stays live in full. Every
     * rejection is also logged at WARN by this class's logger, whether or not a listener is
     * registered, so a broken file on disk is never silent; register a listener to do
     * something about it beyond logging — alert, expose a health signal, page someone.
     *
     * <p>Listeners run on the watcher thread. An exception thrown by one is logged and
     * affects neither the other listeners nor later reloads.
     *
     * @param listener called with the rejected reload's cause
     * @throws NullPointerException if the listener is null
     */
    public void onReloadFailure(Consumer<ReloadFailure> listener) {
        failureListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Stops watching, if watching was enabled.
     *
     * <p>Waits for a reload already in flight to finish, so no listener runs after this
     * returns. Calling it more than once is harmless, and a registry that is closed keeps
     * serving the snapshot it last published.
     *
     * @implNote Bundles are deliberately not closed, including bundles a reload superseded.
     *     An in-flight request may still hold one, and LangChain4j model instances are
     *     immutable and complete normally; they become collectable once no caller holds a
     *     reference.
     */
    @Override
    public void close() {
        ConfigWatcher running = watcher;
        watcher = null;
        if (running != null) {
            running.close();
        }
    }

    /**
     * Re-reads every layer and publishes the result if it differs.
     *
     * @implNote Called only from the watcher thread, so reloads never overlap and the
     *     compare-then-swap below needs no lock. It never throws: a rejected reload is
     *     logged, reported to the failure listeners, and leaves the live snapshot alone.
     */
    void reload() {
        Map<String, LlmBundle> previous = bundles;
        Map<String, LlmBundle> staged;
        try {
            staged = loader.load(previous);
        } catch (RuntimeException e) {
            // ADR-0031: logged whether or not anyone listens. Without this, a typo in a
            // config file makes every later edit stop taking effect with no trace anywhere,
            // and the failure has no caller to be thrown at.
            log.warn("modelrack4j reload rejected; the previous configuration stays live: {}",
                    e.getMessage(), e);
            notify(failureListeners, new ReloadFailure(configFiles, e), "failure");
            return;
        }

        ReloadChange change = ReloadChange.between(previous, staged);
        if (change.isEmpty()) {
            // A wakeup that turned out to change nothing: an unrelated file in a watched
            // directory, or a save that rewrote the same bytes. Publishing here would hand
            // out new instances of identical bundles for no reason.
            return;
        }

        bundles = staged;   // THE swap: one write, whole snapshot, nothing torn
        notify(reloadListeners, change, "reload");
    }

    private static <T> void notify(List<Consumer<T>> listeners, T event, String what) {
        for (Consumer<T> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                log.error("modelrack4j {} listener threw; the reload itself is unaffected",
                        what, e);
            }
        }
    }

    /** Collects the inputs for a registry and builds it. */
    public static final class Builder {

        private List<Path> configFiles = List.of();
        private boolean watch;
        private Duration debounce = DEFAULT_DEBOUNCE;

        private Builder() {
        }

        /**
         * Sets the configuration layers.
         *
         * @param files the layers, <strong>lowest precedence first</strong>, so the last
         *     entry wins on conflict
         * @return this builder
         */
        public Builder configFiles(List<Path> files) {
            this.configFiles = List.copyOf(Objects.requireNonNull(files, "files"));
            return this;
        }

        /**
         * Enables reloading when a configured layer changes.
         *
         * <p>Off by default. When on, the registry starts one daemon thread that watches the
         * directories holding the configured files; {@link LlmRegistry#close()} stops it.
         *
         * @param watch whether to watch for changes
         * @return this builder
         */
        public Builder watch(boolean watch) {
            this.watch = watch;
            return this;
        }

        /**
         * Sets how long the configured files must be quiet before a reload runs.
         *
         * <p>Defaults to 300 ms. One save produces a burst of filesystem events rather than
         * one, and an editor writing through a temporary file briefly leaves no config file
         * in place at all; the quiet period collapses the burst into a single reload and
         * lets a rename settle. Shortening it below the time a writer takes to finish
         * produces reloads of half-written files, which are rejected and reported as
         * failures.
         *
         * @param debounce the quiet period, positive
         * @return this builder
         * @throws IllegalArgumentException if the duration is zero or negative
         */
        public Builder debounce(Duration debounce) {
            Objects.requireNonNull(debounce, "debounce");
            if (debounce.isZero() || debounce.isNegative()) {
                throw new IllegalArgumentException(
                        "debounce must be positive, but was " + debounce);
            }
            this.debounce = debounce;
            return this;
        }

        /**
         * Parses, validates and builds every configured bundle, then starts watching if
         * watching was enabled.
         *
         * @return the registry
         * @throws ConfigValidationException if any layer is unreadable, any block is
         *     invalid, or any provider rejects its configuration
         * @throws UncheckedIOException if watching is enabled and a configured directory
         *     cannot be watched
         */
        public LlmRegistry build() {
            SnapshotLoader loader = new SnapshotLoader(configFiles);
            LlmRegistry registry =
                    new LlmRegistry(configFiles, loader, loader.load(Map.of()));
            if (watch) {
                try {
                    registry.watcher =
                            ConfigWatcher.start(configFiles, debounce, registry::reload);
                } catch (IOException e) {
                    throw new UncheckedIOException(
                            "Cannot watch the configuration files: " + e.getMessage(), e);
                }
            }
            return registry;
        }
    }
}
