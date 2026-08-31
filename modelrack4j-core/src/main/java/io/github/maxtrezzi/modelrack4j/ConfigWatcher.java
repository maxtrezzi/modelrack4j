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

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches the configured files and calls back when they change.
 *
 * <p>The mechanism is the part that is easy to get wrong, and the choices here were measured
 * against a real {@link WatchService} rather than reasoned about (Task 0.8):
 *
 * <ul>
 *   <li><strong>Directories, not files.</strong> {@code WatchService} registers on
 *       directories. Each configured file contributes the directory that contains it,
 *       deduplicated by real path so one directory is registered once.
 *   <li><strong>The path itself, never its target.</strong> For a symlinked config the
 *       directory watched is the one holding the <em>link</em>. Registering on the resolved
 *       target's directory observes nothing at all when a Kubernetes ConfigMap swaps the
 *       link, because the swap creates a new directory rather than touching the old one.
 *   <li><strong>Two filtering modes.</strong> A plain file's events are filtered by
 *       filename, which is what discards the three events of a temp-file-then-rename save.
 *       A symlinked file's are not filtered at all: no event in a ConfigMap swap is named
 *       after the config file, so a filename filter would discard every one of them and the
 *       watcher would silently never reload. The extra wakeups cost a parse and a diff, and
 *       an unchanged snapshot swaps nothing.
 *   <li><strong>Create and modify are the same event.</strong> An ordinary save may arrive
 *       as either, and which one varies between editors and between runs.
 *   <li><strong>Debounce.</strong> One logical write produces a burst of events — one to
 *       four, varying run to run. A quiet period collapses the burst into a single reload.
 * </ul>
 *
 * @implNote The callback runs on this watcher's own daemon thread, one call at a time. That
 *     is no longer what keeps reloads from overlapping — the registry can be reloaded by its
 *     application too, so it serialises reloads itself (ADR-0042). The callback must not
 *     throw; failures belong to the registry's failure listeners.
 */
final class ConfigWatcher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ConfigWatcher.class);

    /** How long to wait before trying to re-register a directory that went away. */
    private static final long REREGISTER_RETRY_NANOS = Duration.ofSeconds(1).toNanos();

    /** How long {@link #close()} waits for the thread to finish an in-flight reload. */
    private static final long CLOSE_TIMEOUT_MILLIS = 5_000;

    private final WatchService service;
    private final long debounceNanos;
    private final Runnable onChange;
    private final Thread thread;
    private final List<WatchedDirectory> directories;

    /** Registrations, replaced when a directory is lost and re-registered. */
    private final Map<WatchKey, WatchedDirectory> keys = new HashMap<>();

    /** Directories whose registration was invalidated, awaiting a retry. */
    private final Set<WatchedDirectory> lost = new HashSet<>();

    private boolean pending;
    private long pendingSince;
    private long retryAt;

    private volatile boolean running = true;

    /**
     * Registers every directory and starts watching.
     *
     * @param configFiles the configured layers
     * @param debounce the quiet period a burst of events must clear before a reload
     * @param onChange run on the watcher thread once per debounced burst
     * @return a running watcher
     * @throws IOException if the watch service cannot be created or a directory cannot be
     *     registered
     */
    static ConfigWatcher start(List<Path> configFiles, Duration debounce, Runnable onChange)
            throws IOException {
        ConfigWatcher watcher = new ConfigWatcher(configFiles, debounce, onChange);
        watcher.thread.start();
        return watcher;
    }

    private ConfigWatcher(List<Path> configFiles, Duration debounce, Runnable onChange)
            throws IOException {
        this.debounceNanos = Objects.requireNonNull(debounce, "debounce").toNanos();
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        this.service = FileSystems.getDefault().newWatchService();
        try {
            this.directories = group(configFiles);
            for (WatchedDirectory directory : directories) {
                register(directory);
            }
        } catch (IOException | RuntimeException e) {
            closeService();
            throw e;
        }
        this.thread = new Thread(this::run, "modelrack4j-config-watcher");
        this.thread.setDaemon(true);
    }

    /**
     * Groups the configured files by the directory that contains them.
     *
     * @implNote Deduplication is by the directory's <em>real</em> path, so two spellings of
     *     one directory do not both register: {@code register} would return the same key
     *     twice and the second registration would displace the first, losing the filenames
     *     the first one was filtering for.
     */
    private static List<WatchedDirectory> group(List<Path> configFiles) throws IOException {
        Map<Path, List<Path>> byDirectory = new LinkedHashMap<>();
        for (Path file : configFiles) {
            // toAbsolutePath, never toRealPath: resolving the configured path here is
            // exactly the mistake that makes a ConfigMap swap invisible.
            Path absolute = file.toAbsolutePath();
            Path parent = absolute.getParent();
            if (parent == null) {
                throw new IOException("Configuration file has no parent directory: " + file);
            }
            byDirectory.computeIfAbsent(parent.toRealPath(), key -> new ArrayList<>())
                    .add(absolute);
        }

        List<WatchedDirectory> watched = new ArrayList<>(byDirectory.size());
        byDirectory.forEach((directory, files) -> watched.add(
                new WatchedDirectory(directory, files)));
        return watched;
    }

    private void register(WatchedDirectory directory) throws IOException {
        directory.refreshMode();
        keys.put(directory.directory.register(service, ENTRY_CREATE, ENTRY_MODIFY,
                ENTRY_DELETE), directory);
    }

    private void run() {
        while (running) {
            WatchKey key = awaitEvent();
            if (!running) {
                return;
            }
            if (key != null) {
                consume(key);
            }
            long now = System.nanoTime();
            reloadIfQuiet(now);
            reregisterIfDue(now);
        }
    }

    /**
     * Blocks until the next event, or until the earliest pending deadline, whichever comes
     * first. Returns {@code null} on a timeout, and on shutdown.
     */
    private WatchKey awaitEvent() {
        long waitMillis = waitMillis();
        try {
            return waitMillis < 0
                    ? service.take()
                    : service.poll(waitMillis, TimeUnit.MILLISECONDS);
        } catch (ClosedWatchServiceException e) {
            running = false;   // close() won the race; this is the normal way out
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
            return null;
        }
    }

    /** Millis until the earliest deadline, or -1 when there is nothing to wake up for. */
    private long waitMillis() {
        long now = System.nanoTime();
        long deadline = Long.MAX_VALUE;
        if (pending) {
            deadline = pendingSince + debounceNanos;
        }
        if (!lost.isEmpty()) {
            deadline = Math.min(deadline, retryAt);
        }
        if (deadline == Long.MAX_VALUE) {
            return -1;
        }
        long remaining = deadline - now;
        // Round up and never return 0: poll(0) would spin.
        return remaining <= 0 ? 1 : Math.max(1, (remaining + 999_999) / 1_000_000);
    }

    private void consume(WatchKey key) {
        WatchedDirectory directory = keys.get(key);
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == OVERFLOW) {
                // Events were dropped. Assume the change was one of them rather than risk
                // missing an edit entirely.
                markPending();
            } else if (directory != null && event.context() instanceof Path context
                    && directory.accepts(context.getFileName().toString())) {
                markPending();
            }
        }
        if (!key.reset()) {
            keys.remove(key);
            if (directory != null) {
                lost.add(directory);
                retryAt = System.nanoTime() + REREGISTER_RETRY_NANOS;
            }
        }
    }

    private void markPending() {
        // Trailing edge: each event pushes the deadline out, so a burst produces one
        // reload once the writer stops rather than one per event.
        pending = true;
        pendingSince = System.nanoTime();
    }

    private void reloadIfQuiet(long now) {
        if (!pending || now - (pendingSince + debounceNanos) < 0) {
            return;
        }
        pending = false;
        // A deployment can replace a plain file with a symlink between one reload and the
        // next, which changes how this directory's events must be filtered.
        directories.forEach(WatchedDirectory::refreshMode);
        try {
            onChange.run();
        } catch (RuntimeException e) {
            // The registry reports its own failures; reaching here means a bug in the
            // callback, and letting it out would silently stop all future reloads.
            log.error("modelrack4j reload callback threw; watching continues", e);
        }
    }

    private void reregisterIfDue(long now) {
        if (lost.isEmpty() || now - retryAt < 0) {
            return;
        }
        for (Iterator<WatchedDirectory> it = lost.iterator(); it.hasNext(); ) {
            WatchedDirectory directory = it.next();
            try {
                register(directory);
                it.remove();
                // A watched directory that disappeared and came back is a redeployment,
                // not a no-op: the events that would have announced the new content were
                // delivered while nothing was registered.
                markPending();
            } catch (IOException e) {
                log.debug("modelrack4j cannot re-register {} yet: {}",
                        directory.directory, e.toString());
            }
        }
        retryAt = now + REREGISTER_RETRY_NANOS;
    }

    /** Stops the watcher thread, waiting for any reload already in flight to finish. */
    @Override
    public void close() {
        running = false;
        closeService();   // unblocks take()/poll() with ClosedWatchServiceException
        if (Thread.currentThread() == thread) {
            // A reload listener closed the registry it was called from. Joining here would
            // be this thread waiting for itself; the loop exits on `running` instead.
            return;
        }
        try {
            thread.join(CLOSE_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeService() {
        try {
            service.close();
        } catch (IOException e) {
            log.debug("modelrack4j watch service did not close cleanly: {}", e.toString());
        }
    }

    /**
     * One registered directory, the configured paths inside it, and how to filter its
     * events.
     */
    private static final class WatchedDirectory {

        private final Path directory;
        private final List<Path> configuredPaths;

        /** Filenames to accept; empty and unused when {@link #acceptAny} is set. */
        private Set<String> fileNames = Set.of();

        /** True when any configured path here is a symlink — see the class comment. */
        private boolean acceptAny;

        WatchedDirectory(Path directory, List<Path> configuredPaths) {
            this.directory = directory;
            this.configuredPaths = List.copyOf(configuredPaths);
        }

        /** Re-decides the filtering mode from the paths as they are right now. */
        void refreshMode() {
            Set<String> names = new HashSet<>();
            boolean any = false;
            for (Path path : configuredPaths) {
                if (Files.isSymbolicLink(path)) {
                    any = true;
                } else {
                    names.add(path.getFileName().toString());
                }
            }
            this.fileNames = names;
            this.acceptAny = any;
        }

        boolean accepts(String fileName) {
            return acceptAny || fileNames.contains(fileName);
        }
    }
}
