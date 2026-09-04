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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
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
 * <p>Configuration is picked up again without a restart, in one of two ways: with
 * {@link Builder#watch(boolean) watching} enabled the registry notices an edit to a
 * configured file by itself, and {@link #reload()} asks for the same work on demand, which is
 * what a layer nothing can watch — a row in a database — needs.
 *
 * <p>A reload is atomic across the <em>whole</em> snapshot: every
 * changed block is rebuilt in a staging area, and only once all of them succeed is a single
 * reference swapped. If any block fails to parse, validate or build, nothing is swapped —
 * the previous snapshot stays live in full and {@link #onReloadFailure} fires once. A
 * half-applied snapshot never exists, not even briefly.
 *
 * <p><strong>How much of that reaches a caller is a separate question.</strong>
 * {@link #get(String)} reads the live configuration on every call, so a reload can land
 * between two consecutive calls, and the two calls then return bundles built from two different
 * generations of the configuration. Where several models have to agree with each other, take a
 * {@link #snapshot()} and look all of them up in it.
 *
 * <p>Unchanged blocks are not rebuilt: the diff is record equality on the parsed
 * configuration, and a name whose block did not change keeps the very bundle instance it
 * had.
 *
 * <h2>Writing a layer back</h2>
 *
 * <p>A layer the application owns can also be written.
 * {@link #store(WritableConfigSource, String)} validates a new text, applies it and stores
 * it as one step, so a text that would break the configuration is refused before anything
 * changes; {@link #storeIfUnchanged(WritableConfigSource, String, String)} does the same
 * only while the layer still holds the text the change was based on, which is what more than
 * one writer needs. A store raises no reload event: its caller already knows what changed,
 * and gets it as a return value.
 *
 * @implNote {@link #get(String)} and {@link #names()} are safe to call from any thread at
 *     any time, including during a reload, and never wait for one. Reloads themselves are
 *     serialised, so listeners still run one reload at a time; they run on whichever thread
 *     caused that reload — a notifier's, or the caller's of {@link #reload()} — and a
 *     listener that blocks delays the next reload. A store takes the same lock and holds it
 *     across the layer's own write, so a slow store delays the next reload too.
 */
public final class LlmRegistry implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LlmRegistry.class);

    /** The default quiet period, ~100x the event burst measured for one write (Task 0.8). */
    private static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(300);

    private final List<Layer> layers;
    private final SnapshotLoader loader;

    /**
     * Serialises reloads. Private, so no caller can take it and interfere: a public lock —
     * or {@code synchronized} on this registry — would let unrelated code block a reload.
     */
    private final Object reloadLock = new Object();
    private final List<Consumer<ReloadChange>> reloadListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ReloadFailure>> failureListeners =
            new CopyOnWriteArrayList<>();

    /**
     * The published snapshot. Reads are lock-free and never wait; writes happen under
     * {@link #reloadLock}, which is what keeps two writers — reloads and stores alike — from
     * racing to publish.
     */
    private volatile Map<String, LlmBundle> bundles;

    /**
     * Empty when nothing notifies this registry, and again once closed.
     *
     * @implNote An {@link AtomicReference} rather than a volatile field because
     *     {@link #close()} must call the notifier's own {@code close()} exactly once.
     *     {@code AutoCloseable} does not promise that a second call is harmless — only
     *     {@code Closeable} does — and this is a public extension point, so two threads
     *     closing at once must not both get through. {@code getAndSet(null)} makes exactly
     *     one of them win.
     */
    private final AtomicReference<ChangeNotifier> notifier = new AtomicReference<>();

    private LlmRegistry(List<Layer> layers, SnapshotLoader loader,
            Map<String, LlmBundle> bundles) {
        this.layers = layers;
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
        return snapshot().get(name);
    }

    /**
     * Returns the current generation, held still, so several lookups agree with each other.
     *
     * <p>{@link #get(String)} reads the live configuration on every call. A reload can
     * therefore land between two consecutive calls, so they return bundles built from
     * different file contents — rare, but reproducible, and a correctness hazard wherever
     * several models are expected to be consistent with one another. This method reads the
     * published generation exactly once and hands it back; everything taken from the result
     * belongs to that one generation.
     *
     * <pre>{@code
     * LlmSnapshot models = registry.snapshot();
     * var fast = models.get("SL");
     * var deep = models.get("SH");   // same generation as fast, guaranteed
     * }</pre>
     *
     * <p>The returned snapshot never updates. Take one per unit of work — per request, per
     * council round — and discard it afterwards. Holding one for the lifetime of the
     * application is the same caching trap in a different form.
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
        return snapshot().names();
    }

    /**
     * Registers a listener called once per successful reload.
     *
     * <p>The listener is secondary: {@link #get(String)} already returns the current bundle,
     * so nothing has to be re-fetched or swapped in response. Use this to log the change, to
     * warm a cache, or to react to a name appearing or disappearing.
     *
     * <p>Listeners run after the swap, so {@link #get(String)} inside one already sees the
     * new snapshot. They run on the thread that caused the reload, inside it, so a listener
     * must not call {@link #reload()}, and must not call {@link #close()} either. Closing
     * stops the notifier, and the reload the listener is running inside still holds the lock
     * that the notifier may be waiting for: with the notifier this library ships, closing
     * from a listener takes five seconds instead of returning at once, and a notifier of
     * your own that waits without a timeout never returns at all. Close the registry from
     * the code that owns it.
     *
     * <p>An exception thrown by a listener is logged and does not affect the reload, the
     * other listeners, or later reloads.
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
     * registered and whether or not the reload had a caller to throw to, so a broken layer
     * is never silent; register a listener to do something about it beyond logging — raise
     * an alert, expose a health signal, notify whoever is on call.
     *
     * <p>Listeners run on the thread that caused the reload. When that is a call to
     * {@link #reload()}, the listeners are told and the exception is thrown to the caller as
     * well. An exception thrown by a listener is logged and affects neither the other
     * listeners nor later reloads.
     *
     * <p><strong>A rejected {@link #store(WritableConfigSource, String)} does not come
     * here.</strong> A store has a caller, and that caller gets the exception; nothing was
     * published and nothing was stored, so there is no state for a listener to react to.
     *
     * @param listener called with the rejected reload's cause
     * @throws NullPointerException if the listener is null
     */
    public void onReloadFailure(Consumer<ReloadFailure> listener) {
        failureListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Stops the notifier, if there is one.
     *
     * <p>What it waits for depends on the notifier. {@link FileChangeNotifier} joins its
     * watcher thread, for up to five seconds, so in practice a reload it had already started
     * finishes first — but the join can time out, and a {@link ChangeNotifier} of your own is
     * only asked to stop and release what it held. So <strong>a listener may still run after
     * this returns</strong>, and an application that must not be called back after closing
     * has to arrange that in the listener. It also does not wait for a {@link #reload()}
     * another thread is running; that call finishes on its own, and its listeners run.
     *
     * <p>Calling this more than once is harmless: the notifier is closed exactly once, no
     * matter how many threads call. A closed registry keeps serving the snapshot it last
     * published, and {@link #reload()} still works on it — closing stops what was watching,
     * not the registry.
     *
     * <p><strong>Do not call this from a reload listener.</strong> A listener runs inside
     * the reload, which holds the lock a notifier's own thread may be waiting for. See
     * {@link #onReload(java.util.function.Consumer)}.
     *
     * @implNote Bundles are deliberately not closed, including bundles a reload superseded.
     *     An in-flight request may still hold one, and LangChain4j model instances are
     *     immutable and complete normally; they become eligible for garbage collection once
     *     no caller holds a reference.
     */
    @Override
    public void close() {
        ChangeNotifier running = notifier.getAndSet(null);
        if (running != null) {
            running.close();
        }
    }

    /**
     * Re-reads every layer now, and publishes the result if it differs.
     *
     * <p>Call this when something outside the registry knows the configuration changed and
     * nothing is watching for it — a row updated in a database, a value refreshed from a
     * configuration service. A registry watching files does not need it: the notifier calls
     * the same code.
     *
     * <p>Reloading is safe to ask for at any time and from any thread. Reloads run one at a
     * time, so this may wait for one already in flight; if that one publishes exactly what
     * this one would have, this returns empty.
     *
     * <p><strong>Do not call this from a reload listener.</strong> Listeners run inside the
     * reload, so a listener that reloads recurses.
     *
     * @return what changed, or empty when the reloaded configuration equals the one already
     *     in effect. The comparison is record equality on the parsed configuration, not on
     *     the text, so reformatting a layer changes nothing and publishes nothing.
     * @throws ConfigValidationException if a layer does not parse or does not validate
     * @throws ConfigAccessException if a layer cannot be read
     * @throws RuntimeException whatever a provider's builder threw. Nothing was swapped: the
     *     previous snapshot stays live in full, and the failure listeners have already been
     *     told.
     */
    public Optional<ReloadChange> reload() {
        // The lock covers read-work-write. Without it, two reloads read the same previous
        // snapshot, both build, and the later write discards the earlier one in silence,
        // after its listeners have already announced it. Readers never take this lock.
        synchronized (reloadLock) {
            Map<String, LlmBundle> previous = bundles;
            Map<String, LlmBundle> staged;
            ReloadChange change;
            try {
                staged = loader.load(previous);
                // Inside the try as well: everything that can fail must be reported, or
                // reloadQuietly() would swallow it with no log and no listener called.
                change = ReloadChange.between(previous, staged);
            } catch (RuntimeException e) {
                // ADR-0031: logged whether or not anyone listens, and whether or not this
                // reload has a caller to throw to. Without it, a typo in a config file makes
                // every later edit stop taking effect with no trace anywhere.
                log.warn(
                        "modelrack4j reload rejected; the previous configuration stays live: {}",
                        e.getMessage(), e);
                notify(failureListeners,
                        new ReloadFailure(Layer.sourcesOf(layers), e), "failure");
                throw e;
            }

            if (change.isEmpty()) {
                // A wakeup that turned out to change nothing: an unrelated file in a watched
                // directory, a save that rewrote the same bytes, or an application asking
                // for a reload after writing what was already there. Publishing here would
                // hand out new instances of identical bundles for no reason.
                return Optional.empty();
            }

            bundles = staged;   // THE swap: one write, whole snapshot, nothing torn
            notify(reloadListeners, change, "reload");
            return Optional.of(change);
        }
    }

    /**
     * Stores a new text for one configuration layer: applied and written together, or
     * neither.
     *
     * <p>The layer must be one this registry was built from, and must be writable — a base
     * layer you ship stays read-only because you never made it a
     * {@link WritableConfigSource}.
     *
     * <pre>{@code
     * registry.store(userLayer, """
     *     llm.SL {
     *       provider   = anthropic
     *       model-name = "claude-opus-5"
     *       api-key    = ${ANTHROPIC_API_KEY}
     *     }
     *     """);
     * }</pre>
     *
     * <p><strong>The text replaces the layer's whole content.</strong> To change part of it,
     * start from {@link ConfigSource#text()} and give back the result. Write only what
     * belongs to this layer: copying in the values it inherits from the layers below freezes
     * them here, and a later change to a lower layer then stops reaching the application —
     * silently, because the result is still a valid configuration.
     *
     * <p>The text is stored exactly as you give it and is never resolved, so a
     * {@code ${VAR}} in it stays a {@code ${VAR}} and no resolved secret is written.
     *
     * <p><strong>No reload event is fired for your own change</strong>, and no flag is needed
     * to arrange that. The change is applied before it is stored, so a file watcher waking up
     * afterwards re-reads, finds what is already live, and publishes nothing. A listener
     * hears about changes made by somebody else, which is what a listener is for.
     *
     * <p><strong>Reading a layer and storing it back is two calls, and this one does not hold
     * them together.</strong> Each store is atomic against reloads and against other stores,
     * but two threads that both read {@link ConfigSource#text()} and then store lose one of
     * the two changes. Use {@link #storeIfUnchanged(WritableConfigSource, String, String)}
     * where more than one writer is possible.
     *
     * @param target the layer to write, which must be one of this registry's own
     * @param text the layer's new content, as HOCON
     * @return what changed, or empty when the new text means exactly what was already live —
     *     the comparison is on the parsed configuration, so a reformatting is stored and
     *     reported as no change
     * @throws ConfigValidationException if the layer is not one of this registry's own,
     *     or if the text does not parse or does not validate
     * @throws ConfigAccessException if the text validates but cannot be stored, or if
     *     another layer cannot be read while it is validated. The previous configuration is
     *     back in place and no listener ran.
     * @throws RuntimeException whatever the layer's own
     *     {@link WritableConfigSource#write(String)} threw, or a provider's builder. The
     *     previous configuration is back in place and no listener ran.
     * @throws NullPointerException if either argument is null
     * @implNote The order is the whole contract. The text is staged and validated before
     *     anything is published or stored, so a broken one is refused with nothing changed.
     *     The swap happens <em>before</em> the write, which is what leaves a waking watcher
     *     with an empty difference. If the write then fails, the previous snapshot goes back
     *     and the caller is told by an exception; no listener ran, because listeners do not
     *     run for a store at all.
     */
    public Optional<ReloadChange> store(WritableConfigSource target, String text) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(text, "text");
        requireOwnLayer(target);
        synchronized (reloadLock) {
            return storeHoldingTheLock(target, text);
        }
    }

    /**
     * Stores a new text for one configuration layer, but only if the layer still holds the
     * text the change was based on.
     *
     * <p>This is the answer to more than one writer. Read the layer, work out the new text,
     * and pass both: if somebody else stored the layer in between, the store is refused
     * instead of erasing their change, and {@link StaleLayerException#current()} gives the
     * text to rebase onto.
     *
     * <pre>{@code
     * String base = layer.text();
     * while (true) {
     *     try {
     *         registry.storeIfUnchanged(layer, base, withMyChangeApplied(base));
     *         break;
     *     } catch (StaleLayerException stale) {
     *         base = stale.current();
     *     }
     * }
     * }</pre>
     *
     * <p>The comparison is on the text, character for character, not on what it means. A
     * layer somebody reformatted or re-commented is a layer that moved, and this refuses it:
     * the value of the check is that it sees every change, and a comment is a change a person
     * made on purpose.
     *
     * <p>A trailing newline counts too. {@link ConfigSource#text()} gives the layer's content
     * back as it is, final newline included, so anything that strips it — a shell
     * {@code $(cat layer.conf)} does — produces an {@code expected} that no longer matches.
     * Pass the text on as you received it.
     *
     * <p>Everything else matches {@link #store(WritableConfigSource, String)} — validation
     * before anything is published, rollback if the write fails, and no reload event for your
     * own change.
     *
     * @param target the layer to write, which must be one of this registry's own
     * @param expected the text the change was based on, as {@link ConfigSource#text()}
     *     returned it
     * @param text the layer's new content, as HOCON
     * @return what changed, or empty when the new text means exactly what was already live
     * @throws StaleLayerException if the layer no longer holds {@code expected}. Nothing was
     *     published and nothing was stored.
     * @throws ConfigValidationException if the layer is not one of this registry's own,
     *     or if the text does not parse or does not validate
     * @throws ConfigAccessException if the text validates but cannot be stored, or if
     *     another layer cannot be read while it is validated. The previous configuration is
     *     back in place and no listener ran.
     * @throws RuntimeException whatever the layer's own
     *     {@link WritableConfigSource#write(String)} threw, or a provider's builder. The
     *     previous configuration is back in place and no listener ran.
     * @throws NullPointerException if any argument is null
     * @implNote The layer is read again inside the reload lock rather than trusting anything
     *     read earlier, so the comparison and the write cannot be separated by another
     *     writer. That read is I/O for a file layer, which is why it happens once and only
     *     for this method. This is also why the library offers no version token: a caller
     *     that wants a short condition — an HTTP {@code ETag}, say — derives it from
     *     {@link ConfigSource#text()} and passes that same text here, and the re-read closes
     *     the gap the token cannot (ADR-0052). The reference manual carries the recipe.
     */
    public Optional<ReloadChange> storeIfUnchanged(
            WritableConfigSource target, String expected, String text) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(text, "text");
        requireOwnLayer(target);
        synchronized (reloadLock) {
            String current = target.text();
            if (!current.equals(expected)) {
                throw new StaleLayerException(target.id(), current);
            }
            return storeHoldingTheLock(target, text);
        }
    }

    /**
     * Validates, publishes and stores, with {@link #reloadLock} already held.
     *
     * @param target the layer to write
     * @param text the layer's new content
     * @return what changed, or empty when nothing did
     */
    private Optional<ReloadChange> storeHoldingTheLock(
            WritableConfigSource target, String text) {
        StagedWrite staged = StagedWrite.prepare(target, text);
        try {
            Map<String, LlmBundle> previous = bundles;
            Map<String, LlmBundle> next = loader.load(previous, staging(target, staged));
            ReloadChange change = ReloadChange.between(previous, next);

            bundles = next;   // published, but nobody is told: this is the caller's change
            try {
                staged.commit();
            } catch (RuntimeException notStored) {
                // Back to exactly the state before the store. Nothing was announced, so
                // nothing has to be un-announced.
                bundles = previous;
                throw notStored;
            }
            return change.isEmpty() ? Optional.empty() : Optional.of(change);
        } finally {
            staged.discard();
        }
    }

    /**
     * Refuses a layer this registry does not read.
     *
     * @param target the layer named by the caller
     * @throws ConfigValidationException if it is not one of this registry's sources
     */
    private void requireOwnLayer(WritableConfigSource target) {
        List<ConfigSource> sources = Layer.sourcesOf(layers);
        if (!sources.contains(target)) {
            throw new ConfigValidationException("The layer '" + target.id() + "' is not one of"
                    + " this registry's configuration sources, so it cannot be stored through"
                    + " it. Its layers are: " + sources.stream().map(ConfigSource::id).toList());
        }
    }

    private List<Layer> staging(WritableConfigSource target, StagedWrite staged) {
        List<Layer> staging = new ArrayList<>(layers.size());
        for (Layer layer : layers) {
            staging.add(layer.source().equals(target) ? Layer.of(staged.source()) : layer);
        }
        return List.copyOf(staging);
    }

    /**
     * Reloads on behalf of a {@link ChangeNotifier}, which has no caller to throw to.
     *
     * @implNote The rejection is not lost by being swallowed here: {@link #reload()} has
     *     already logged it at WARN and delivered it to the failure listeners. Rethrowing
     *     would only reach the notifier's own thread (ADR-0028).
     */
    void reloadQuietly() {
        try {
            reload();
        } catch (RuntimeException alreadyLoggedAndReported) {
            // Deliberately swallowed; see the note above.
        }
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

        private List<ConfigSource> sources = List.of();
        private boolean watch;
        private Duration debounce = DEFAULT_DEBOUNCE;
        private ChangeNotifier notifier;

        private Builder() {
        }

        /**
         * Sets the configuration layers, as files. The shorthand for the common case.
         *
         * <p>Exactly equivalent to {@link #sources(List)} over
         * {@link ConfigSource#ofFile(Path)}, including for {@link #watch(boolean)}, which
         * finds the files among the layers either way.
         *
         * <p>This and {@link #sources(List)} set the same thing; the last call wins.
         *
         * @param files the layers, <strong>lowest precedence first</strong>, so the last
         *     entry wins on conflict
         * @return this builder
         */
        public Builder configFiles(List<Path> files) {
            List<Path> copy = List.copyOf(Objects.requireNonNull(files, "files"));
            List<ConfigSource> asSources = new ArrayList<>(copy.size());
            for (Path file : copy) {
                asSources.add(ConfigSource.ofFile(file));
            }
            this.sources = List.copyOf(asSources);
            return this;
        }

        /**
         * Sets the configuration layers, from anywhere.
         *
         * <p>Use this when a layer is not a file — a row in a database, a value from a
         * configuration service, text built in memory. Layers of different kinds mix freely:
         * a base file under a database override is an ordinary list.
         *
         * <p>{@link #watch(boolean)} watches the layers among these that are files —
         * {@link ConfigSource#ofFile(Path)} and {@link ConfigSource#ofWritableFile(Path)} —
         * and leaves the others alone, because the library cannot know how to watch them.
         * For those, call {@link LlmRegistry#reload()} when the configuration changes, or
         * supply a {@link #notifier(ChangeNotifier)} that knows when it does.
         *
         * <p>This and {@link #configFiles(List)} set the same thing; the last call wins.
         *
         * @param sources the layers, <strong>lowest precedence first</strong>
         * @return this builder
         */
        public Builder sources(List<ConfigSource> sources) {
            this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
            return this;
        }

        /**
         * Supplies something that tells the registry when the configuration changed.
         *
         * <p>For a mechanism the library does not provide: a database
         * {@code LISTEN}/{@code NOTIFY}, a Kubernetes informer, a message queue. Also for a
         * file behind a {@link ConfigSource} of your own, which {@link #watch(boolean)}
         * cannot recognise as a file. For the file layers this library makes,
         * {@link #watch(boolean)} builds the right notifier already, and the two cannot both
         * be set.
         *
         * <p>The registry owns it from {@link #build()} on, and closes it in
         * {@link LlmRegistry#close()}.
         *
         * @param notifier the notifier, or {@code null} for none
         * @return this builder
         */
        public Builder notifier(ChangeNotifier notifier) {
            this.notifier = notifier;
            return this;
        }

        /**
         * Enables reloading when a configured layer changes.
         *
         * <p>Off by default. When on, the registry starts one daemon thread that watches the
         * directories holding the configured files; {@link LlmRegistry#close()} stops it.
         *
         * <p>It watches the layers that are files and ignores the rest, so a registry that
         * mixes a file with a layer of another kind is watched over its file half. At least
         * one layer must be a file: with none, there is nothing to watch and {@link #build()}
         * says so rather than watching nothing in silence — supply a
         * {@link #notifier(ChangeNotifier)} or call {@link LlmRegistry#reload()} instead.
         *
         * <p>A file layer is one this library made, through {@link ConfigSource#ofFile(Path)}
         * or {@link ConfigSource#ofWritableFile(Path)}. Your own {@link ConfigSource} that
         * reads a file cannot say so, and is not watched; give a
         * {@link #notifier(ChangeNotifier)} for it.
         *
         * @param watch whether to watch the configured files for changes
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
         * Parses, validates and builds every configured bundle, then starts the notifier if
         * there is one.
         *
         * @return the registry
         * @throws ConfigValidationException if no layer was given, two layers share an id,
         *     any block is invalid, any provider rejects its configuration, or watching was
         *     asked for and no layer is a file
         * @throws ConfigAccessException if any layer cannot be read
         * @throws UncheckedIOException if watching is enabled and a configured directory
         *     cannot be watched
         */
        public LlmRegistry build() {
            List<Layer> layers = Layer.of(ConfigSources.validated(sources));
            // Chosen before the layers are loaded so that an impossible combination —
            // watch(true) with no files — is reported before any work, rather than after a
            // slow load.
            ChangeNotifier chosen = chooseNotifier(layers);
            LlmRegistry registry;
            try {
                SnapshotLoader loader = new SnapshotLoader(layers);
                registry = new LlmRegistry(layers, loader, loader.load(Map.of()));
            } catch (RuntimeException e) {
                // A bad layer must not leave a notifier the caller thinks we took ownership
                // of: build() does not return, so nobody else can close it.
                closeSuppressing(chosen, e);
                throw e;
            }
            if (chosen != null) {
                registry.notifier.set(chosen);
                startOrClose(chosen, registry);
            }
            return registry;
        }

        /**
         * Starts the notifier, closing it if starting fails.
         *
         * @implNote Without this, a notifier that allocated something before throwing would
         *     be left open with nobody holding a reference to close it: {@code build()} does
         *     not return, so the caller never sees the registry that owns it.
         */
        private static void startOrClose(ChangeNotifier notifier, LlmRegistry registry) {
            try {
                notifier.start(registry::reloadQuietly);
            } catch (RuntimeException e) {
                closeSuppressing(notifier, e);
                throw e;
            }
        }

        /** Closes {@code notifier}, attaching a failing close to {@code primary}. */
        private static void closeSuppressing(ChangeNotifier notifier, RuntimeException primary) {
            if (notifier == null) {
                return;
            }
            try {
                notifier.close();
            } catch (RuntimeException closeFailed) {
                primary.addSuppressed(closeFailed);
            }
        }

        /**
         * Picks what will tell the registry that the configuration changed.
         *
         * @param layers the validated layers, whose file members {@code watch(true)} watches
         * @return the notifier, or {@code null} when neither was asked for
         * @throws ConfigValidationException if both a notifier and watching were asked for,
         *     or if watching was asked for and no layer is a file
         * @implNote The paths come from the layers themselves rather than from a field only
         *     {@code configFiles(...)} filled, which is what made a registry whose every
         *     layer was a file refuse to be watched (ADR-0050). Each layer answers for
         *     itself through {@link Layer#watchTarget()} rather than being recognised here
         *     (ADR-0051), and what it answers is the configured path, not the one it
         *     resolves to, so the watcher still registers on the directory of a symlink
         *     rather than of its target (ADR-0024).
         */
        private ChangeNotifier chooseNotifier(List<Layer> layers) {
            if (notifier != null) {
                if (watch) {
                    throw new ConfigValidationException(
                            "Set one of watch(true) and notifier(...), not both: watch(true)"
                                    + " builds a file notifier of its own");
                }
                return notifier;
            }
            if (!watch) {
                return null;
            }
            List<Path> files = new ArrayList<>(layers.size());
            for (Layer layer : layers) {
                layer.watchTarget().ifPresent(files::add);
            }
            if (files.isEmpty()) {
                throw new ConfigValidationException(
                        "watch(true) watches configuration files, and none of these layers is"
                                + " one: " + ids(layers) + ". Supply a ChangeNotifier, or call"
                                + " reload() when the configuration changes.");
            }
            return FileChangeNotifier.of(files, debounce);
        }

        /** @return the layers' identifiers, for a message that says which ones they are */
        private static String ids(List<Layer> layers) {
            List<String> names = new ArrayList<>(layers.size());
            for (Layer layer : layers) {
                names.add(layer.source().id());
            }
            return names.toString();
        }
    }
}
