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
import java.util.TreeSet;

/**
 * What one successful reload changed, reported as three sets of configuration names.
 *
 * <p>Exactly one of these is delivered per snapshot swap, however many names or files were
 * involved. It is deliberately snapshot-level: a per-name callback would let an application
 * observe one name's new configuration next to another's old one, which is a correctness
 * hazard when several models cooperate. Per-name notifications are derivable from these
 * sets; they are never delivered independently.
 *
 * <p>The sets are disjoint, sorted, and unmodifiable. A reload that resolves to the same
 * configuration produces no change object at all, because nothing is swapped.
 *
 * @param updated names present before and after, whose configuration differs
 * @param added names that did not exist before this reload
 * @param removed names that no longer exist; {@link LlmRegistry#get(String)} now throws for
 *     these
 */
public record ReloadChange(Set<String> updated, Set<String> added, Set<String> removed) {

    /** @throws NullPointerException if any set is null */
    public ReloadChange {
        updated = sortedCopy(updated, "updated");
        added = sortedCopy(added, "added");
        removed = sortedCopy(removed, "removed");
    }

    /**
     * Returns whether this change reports nothing at all.
     *
     * @return {@code true} when no name was updated, added or removed
     */
    public boolean isEmpty() {
        return updated.isEmpty() && added.isEmpty() && removed.isEmpty();
    }

    /**
     * Diffs two snapshots by record equality on the parsed configuration.
     *
     * @param before the live snapshot
     * @param after the staged snapshot
     * @return the change between them
     */
    static ReloadChange between(Map<String, LlmBundle> before, Map<String, LlmBundle> after) {
        Set<String> updated = new TreeSet<>();
        Set<String> added = new TreeSet<>();
        Set<String> removed = new TreeSet<>(before.keySet());
        removed.removeAll(after.keySet());

        for (Map.Entry<String, LlmBundle> entry : after.entrySet()) {
            LlmBundle previous = before.get(entry.getKey());
            if (previous == null) {
                added.add(entry.getKey());
            } else if (!previous.config().equals(entry.getValue().config())) {
                updated.add(entry.getKey());
            }
        }
        return new ReloadChange(updated, added, removed);
    }

    private static Set<String> sortedCopy(Set<String> names, String what) {
        return Collections.unmodifiableSet(
                new TreeSet<>(Objects.requireNonNull(names, what)));
    }
}
