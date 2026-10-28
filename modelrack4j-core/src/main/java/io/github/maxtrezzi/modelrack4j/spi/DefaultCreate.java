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
package io.github.maxtrezzi.modelrack4j.spi;

import java.util.function.Supplier;

/**
 * What the two-argument {@code create} defaults of {@link ProviderFactory} run, so that a factory
 * whose two {@code create} methods call each other fails with a message instead of recursing.
 *
 * <p>The SPI tells an implementor to write the one-argument method as a call to the two-argument
 * one. A factory that does that and forgets to override the two-argument method reaches its
 * default, which calls the one-argument method, which calls the default again. The compiler
 * accepts it, because the two-argument method has a body, and the result was a
 * {@code StackOverflowError}: an {@code Error}, which {@code LlmRegistry.reload()} and the
 * watcher loop do not catch, so on the watcher thread it ended the thread in silence.
 *
 * <p>Not API: package-private, and used only by the defaults.
 */
final class DefaultCreate {

    /** Whether a two-argument default is already running on this thread. */
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private DefaultCreate() {
    }

    /**
     * Runs the one-argument method on behalf of a two-argument default.
     *
     * @param <M> what the method builds
     * @param factory the factory, for the message
     * @param method the method's name, for the message
     * @param oneArgument the call to the one-argument method
     * @return what the one-argument method returned
     * @throws IllegalStateException if the one-argument method calls a two-argument default,
     *     which would otherwise recurse until the stack overflows
     * @implNote A thread-local flag rather than a depth counter: a legitimate factory never
     *     enters a default from inside a default, so a second entry on the same thread can only
     *     be the loop. It is removed in {@code finally}, because the registry builds on a pooled
     *     watcher thread as well as on the caller's.
     */
    static <M> M delegate(ProviderFactory factory, String method, Supplier<M> oneArgument) {
        if (ACTIVE.get()) {
            throw new IllegalStateException("Provider factory " + factory.getClass().getName()
                    + " implements " + method + "(LlmConfig) by calling " + method
                    + "(LlmConfig, ModelContext), which it does not override, so each method"
                    + " calls the other. Override the two-argument method.");
        }
        ACTIVE.set(true);
        try {
            return oneArgument.get();
        } finally {
            ACTIVE.remove();
        }
    }
}
