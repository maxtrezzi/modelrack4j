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

/**
 * Tells the registry when its configuration has changed.
 *
 * <p>A registry needs one of these only when something can notice a change on its own. The
 * built-in implementation watches files; a source that cannot be watched — a database row, a
 * value from a configuration service — needs no notifier at all, and the application calls
 * {@link LlmRegistry#reload()} when it knows the configuration moved.
 *
 * <p>Implement this to plug in a mechanism the library does not provide: a database
 * {@code LISTEN}/{@code NOTIFY}, a Kubernetes informer, a message from a queue. The registry
 * never asks where the configuration lives, only that it is told when it changed.
 *
 * @implSpec {@link #start(Runnable)} is called once, from the thread that builds the
 *     registry. The callback may run on any thread the implementation likes, including many
 *     of them: the registry serialises reloads internally. The callback never throws.
 */
public interface ChangeNotifier extends AutoCloseable {

    /**
     * Begins watching, and calls {@code onChange} whenever the configuration may have
     * changed.
     *
     * <p>A spurious call costs a re-read and a comparison, and publishes nothing when the
     * configuration turns out to be identical, so an implementation that cannot tell exactly
     * what happened should call rather than stay silent.
     *
     * @param onChange what to run on a change, never {@code null}
     * @throws IllegalStateException if called more than once
     */
    void start(Runnable onChange);

    /**
     * Stops watching and releases whatever was held. Called by {@link LlmRegistry#close()},
     * and required not to throw a checked exception.
     */
    @Override
    void close();
}
