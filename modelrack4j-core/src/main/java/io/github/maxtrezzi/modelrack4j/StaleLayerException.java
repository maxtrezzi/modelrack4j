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

import java.util.Objects;

/**
 * Thrown by {@link LlmRegistry#storeIfUnchanged(WritableConfigSource, String, String)} when
 * the layer no longer holds the text the caller based its change on.
 *
 * <p>Nothing is wrong with the text that was offered: somebody else wrote the layer in the
 * meantime, and storing now would erase their change. This is the retryable failure —
 * {@link #current()} gives the text the layer holds now, so the caller can apply its change
 * to that and try again:
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
 * <p><strong>It is not always somebody else.</strong> The comparison is on the text,
 * character for character, so an {@code expected} that was reshaped on the way in is refused
 * although the layer never moved. A shell {@code $(cat layer.conf)} drops the final newline,
 * and an HTTP client that trims a response body does the same. Pass
 * {@link ConfigSource#text()} on as you received it, and this exception stays what it says it
 * is: a lost race.
 *
 * <p>It is deliberately not a {@link ConfigValidationException}: a caller that catches
 * validation failures wants to report them to a person, and a lost race is something the
 * program can handle by itself.
 */
public final class StaleLayerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String layerId;
    private final String current;

    /**
     * Creates an exception naming the layer that moved on.
     *
     * @param layerId the layer's {@link ConfigSource#id()}, never {@code null}
     * @param current the text the layer holds now, never {@code null}
     */
    public StaleLayerException(String layerId, String current) {
        super("The layer '" + Objects.requireNonNull(layerId, "layerId") + "' has changed"
                + " since the text this store was based on was read, so storing now would"
                + " erase somebody else's change. Apply your change to the layer's current"
                + " text and try again. The comparison is character for character and"
                + " includes the final newline, so an expected text that lost that newline"
                + " on the way here is refused even when nothing else about it changed.");
        this.layerId = layerId;
        this.current = Objects.requireNonNull(current, "current");
    }

    /**
     * Returns the layer that could not be stored.
     *
     * @return the layer's {@link ConfigSource#id()}
     */
    public String layerId() {
        return layerId;
    }

    /**
     * Returns the text the layer held when the store was refused.
     *
     * @return the layer's text at that moment, to rebase the change onto
     * @implNote Read while the registry's reload lock was held, so it is the text a store
     *     made immediately afterwards would have replaced — but nothing stops a third writer
     *     from moving the layer on again before the retry, which is why the retry is a loop
     *     rather than a second attempt.
     */
    public String current() {
        return current;
    }
}
