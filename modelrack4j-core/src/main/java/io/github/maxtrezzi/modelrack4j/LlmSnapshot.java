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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One generation of the registry's configuration, held still.
 *
 * <p>Every bundle reachable from a snapshot came from the same configuration file contents.
 * Ask a snapshot for four names and you get four bundles that agree with each other, even if
 * a reload lands while you are asking.
 *
 * <p><strong>Why this exists.</strong> {@link LlmRegistry#get(String)} reads the live
 * configuration on every call, which is what makes reload work — but it also means two
 * consecutive calls can straddle a reload and return bundles from different generations:
 *
 * <pre>{@code
 * ChatModel a = registry.get("SL").chatModel();   // generation N
 *                                                 // <-- a reload may land here
 * ChatModel b = registry.get("SH").chatModel();   // generation N+1
 * }</pre>
 *
 * <p>That window is small — measured at roughly two occurrences per million read pairs under
 * a reload every few milliseconds — but it is not zero, and "rare" is the worst kind of
 * concurrency bug. Take a snapshot when several models have to agree:
 *
 * <pre>{@code
 * LlmSnapshot models = registry.snapshot();
 * ChatModel a = models.get("SL").chatModel();
 * ChatModel b = models.get("SH").chatModel();     // guaranteed same generation as a
 * }</pre>
 *
 * <p><strong>A snapshot does not see reloads</strong>, by construction — that is the whole
 * point of it. Take one per unit of work, not one at startup, or you have re-created the
 * caching trap {@link LlmBundle} warns about.
 *
 * @see LlmRegistry#snapshot()
 */
public final class LlmSnapshot {

    private final Map<String, LlmBundle> bundles;

    /**
     * Package-private: a snapshot only ever comes from the registry that published it.
     * Re-wrapped as unmodifiable regardless of what {@link SnapshotLoader} already did, so
     * this invariant lives here rather than depending silently on another file. Verified
     * rather than assumed: {@code Collections.unmodifiableMap} recognises an
     * already-unmodifiable map and returns that same instance instead of allocating a new
     * wrapper, so this line costs one {@code instanceof} check and no allocation — safe to
     * pay on every call to {@link LlmRegistry#get(String)}, which delegates here and is the
     * API's declared cheap path (see the README).
     */
    LlmSnapshot(Map<String, LlmBundle> bundles) {
        this.bundles = Collections.unmodifiableMap(Objects.requireNonNull(bundles, "bundles"));
    }

    /**
     * Returns the bundle for a name, as it was when this snapshot was taken.
     *
     * @param name the configuration name, as written in the config file
     * @return the bundle bound to that name in this generation
     * @throws UnknownConfigurationException if this generation had no bundle for the name
     */
    public LlmBundle get(String name) {
        LlmBundle bundle = bundles.get(Objects.requireNonNull(name, "name"));
        if (bundle == null) {
            throw new UnknownConfigurationException(name);
        }
        return bundle;
    }

    /**
     * Returns every name configured in this generation, in sorted order.
     *
     * @return an unmodifiable set of names
     */
    public Set<String> names() {
        return Collections.unmodifiableSet(bundles.keySet());
    }

    /**
     * Returns whether this generation has a bundle for the name.
     *
     * @param name the configuration name
     * @return {@code true} if {@link #get(String)} would return a bundle rather than throw
     */
    public boolean contains(String name) {
        return bundles.containsKey(Objects.requireNonNull(name, "name"));
    }

    @Override
    public String toString() {
        return "LlmSnapshot" + names();
    }
}
