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
import java.util.List;
import java.util.Objects;

/**
 * The {@link ChangeNotifier} for configuration held in files.
 *
 * <p>It registers on the directories holding the given files rather than on the files
 * themselves, treats creation and modification alike, collapses the burst of events one save
 * produces into a single call, and re-registers a directory that goes away and comes back.
 * The reasoning is in ADR-0013 and ADR-0024, and none of it changed when notification became
 * an interface.
 *
 * <p>Built for you by {@link LlmRegistry.Builder#watch(boolean)}, which is the usual way to
 * get one. Construct it yourself when the layers were given through
 * {@link LlmRegistry.Builder#sources(java.util.List)} — a file layer under a database layer,
 * say — and the file half should still be watched.
 *
 * @implNote One instance covers the whole list, sharing a single watch service and a single
 *     daemon thread across the deduplicated set of parent directories. One notifier per file
 *     would spend a thread apiece on files that usually sit in the same directory.
 */
public final class FileChangeNotifier implements ChangeNotifier {

    private enum State {
        NEW,
        STARTED,
        CLOSED
    }

    private final List<Path> files;
    private final Duration debounce;

    /**
     * Guards the transition between states. Private, so nothing outside can hold it while
     * this notifier is starting or stopping.
     */
    private final Object lock = new Object();

    private State state = State.NEW;

    /** Null until a successful {@link #start(Runnable)}, and again after {@link #close()}. */
    private ConfigWatcher watcher;

    private FileChangeNotifier(List<Path> files, Duration debounce) {
        this.files = files;
        this.debounce = debounce;
    }

    /**
     * Returns a notifier for the given files.
     *
     * @param files the files to watch, at least one
     * @param debounce the quiet period that collapses one save's burst of events
     * @return a notifier, not yet started
     * @throws ConfigValidationException if the list is empty or the debounce is not positive
     */
    public static FileChangeNotifier of(List<Path> files, Duration debounce) {
        List<Path> copy = List.copyOf(Objects.requireNonNull(files, "files"));
        Objects.requireNonNull(debounce, "debounce");
        if (copy.isEmpty()) {
            throw new ConfigValidationException("At least one file is required to watch");
        }
        if (debounce.isZero() || debounce.isNegative()) {
            throw new ConfigValidationException("debounce must be positive, was " + debounce);
        }
        return new FileChangeNotifier(copy, debounce);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if this notifier is already started, or was closed — a
     *     closed one is spent, not reusable
     * @throws UncheckedIOException if a watched directory cannot be registered
     */
    @Override
    public void start(Runnable onChange) {
        Objects.requireNonNull(onChange, "onChange");
        // Tested and set under one lock. Read-then-assign on a volatile field would let two
        // callers both pass the check and start two watcher threads, one of which would then
        // be unreachable and never closed.
        synchronized (lock) {
            if (state != State.NEW) {
                throw new IllegalStateException(
                        "A FileChangeNotifier starts once; this one is already " + state);
            }
            try {
                watcher = ConfigWatcher.start(files, debounce, onChange);
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Cannot watch the configuration files: " + e.getMessage(), e);
            }
            state = State.STARTED;
        }
    }

    @Override
    public void close() {
        ConfigWatcher running;
        synchronized (lock) {
            running = watcher;
            watcher = null;
            state = State.CLOSED;
        }
        // Outside the lock on purpose: closing joins the watcher thread, and holding a lock
        // across a join is how a future callback that needed this lock would deadlock.
        if (running != null) {
            running.close();
        }
    }
}
