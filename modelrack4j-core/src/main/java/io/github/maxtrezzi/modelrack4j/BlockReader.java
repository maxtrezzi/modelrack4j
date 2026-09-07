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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads one named configuration block and remembers which paths it was asked for.
 *
 * <p>The set of keys this library's schema knows is whatever {@link LlmConfig#fromBlock} asks
 * this reader for. Nothing declares it separately, which is the point: a declared list is a
 * second copy of the same truth, and the day someone adds a key to the parse and forgets the
 * list, every file using that key is rejected (ADR-0056).
 *
 * @implNote Required values are collected rather than thrown on, so the pass always reaches
 *     the end and the recorded set is complete. Otherwise a block whose {@code model-name} is
 *     misspelled would stop the parse at {@code model-name} and report every key after it —
 *     {@code timeout}, {@code streaming}, all of them present from the defaults — as unknown.
 *     {@link #rethrowFirstMissing()} then produces the genuine exception, by asking Typesafe
 *     Config for the value again, so the message a user already knows is unchanged.
 */
final class BlockReader {

    private final Config block;
    private final String prefix;
    private final Set<String> asked;
    private final List<String> missing;

    BlockReader(Config block) {
        this(Objects.requireNonNull(block, "block"), "", new HashSet<>(), new ArrayList<>());
    }

    private BlockReader(Config block, String prefix, Set<String> asked, List<String> missing) {
        this.block = block;
        this.prefix = prefix;
        this.asked = asked;
        this.missing = missing;
    }

    /**
     * A reader over a sub-block, recording into the same set under a longer prefix.
     *
     * @param path the sub-block, which is itself recorded as known
     * @return a reader over it
     */
    BlockReader scoped(String path) {
        record(path);
        return new BlockReader(block.getConfig(path), prefix + path + ".", asked, missing);
    }

    /** @return whether the path is present, recording it as one the schema knows */
    boolean has(String path) {
        record(path);
        return block.hasPath(path);
    }

    String string(String path) {
        record(path);
        return block.getString(path);
    }

    /** @return the value, or a placeholder when it is missing, which is remembered instead */
    String requiredString(String path) {
        record(path);
        if (!block.hasPath(path)) {
            missing.add(prefix + path);
            return "";
        }
        return block.getString(path);
    }

    int requiredInt(String path) {
        record(path);
        if (!block.hasPath(path)) {
            missing.add(prefix + path);
            return 0;
        }
        return block.getInt(path);
    }

    boolean bool(String path) {
        record(path);
        return block.getBoolean(path);
    }

    double dbl(String path) {
        record(path);
        return block.getDouble(path);
    }

    Duration duration(String path) {
        record(path);
        return block.getDuration(path);
    }

    ConfigValue value(String path) {
        record(path);
        return block.getValue(path);
    }

    /**
     * Whether any required value was found missing so far.
     *
     * @return true when {@link #rethrowFirstMissing()} will throw
     * @implNote For a caller that would otherwise build a validating object out of a
     *     placeholder. Everything else defers construction until after
     *     {@link #requireNoUnknownKeys(String)} has run, which is what lets a misspelling be
     *     named rather than the missing key it hid.
     */
    boolean anyMissing() {
        return !missing.isEmpty();
    }

    /**
     * Rejects every key in the block that the parse never asked for.
     *
     * @param name the configuration name, for the message
     * @throws ConfigValidationException naming every offending key with its layer and line
     * @implNote Enumerated with {@code entrySet()}, which yields leaf paths — so a misspelling
     *     inside {@code memory} is reported as {@code memory.max-mesages} rather than as
     *     {@code memory} — and which leaves out a key a higher layer cleared with
     *     {@code = null}, so ADR-0032's clearing idiom is not mistaken for a stray key.
     *     Everything under {@code custom-properties} is known by prefix: that block is the
     *     declared home for what this library does not interpret (ADR-0055).
     */
    void requireNoUnknownKeys(String name) {
        Map<String, ConfigValue> unknown = new TreeMap<>();
        for (Map.Entry<String, ConfigValue> entry : block.entrySet()) {
            String path = entry.getKey();
            if (!asked.contains(path) && !isUnderAKnownBlock(path)) {
                unknown.put(path, entry.getValue());
            }
        }
        if (unknown.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder("llm." + name + " has "
                + (unknown.size() == 1 ? "a key" : unknown.size() + " keys")
                + " this library does not know:");
        for (Map.Entry<String, ConfigValue> entry : unknown.entrySet()) {
            message.append("\n  ").append(entry.getKey())
                    .append(" (").append(entry.getValue().origin().description()).append(')');
        }
        message.append("\nCheck the spelling. Values your own application reads belong in the")
                .append(" block's ").append(LlmConfig.CUSTOM_PROPERTIES).append(" section,")
                .append(" which this library carries without reading.");
        throw new ConfigValidationException(message.toString());
    }

    /**
     * Reproduces the failure a missing required value would have caused.
     *
     * @implNote By asking Typesafe Config for the value again rather than by composing a
     *     message, so the exception a user sees is the one they saw before this reader
     *     existed, cause and all.
     */
    void rethrowFirstMissing() {
        for (String path : missing) {
            // Prefixed, and asked of the root block: a scoped reader's own block is gone by
            // now, and the root spells the path the way the file does.
            block.getString(path);
        }
    }

    private boolean isUnderAKnownBlock(String path) {
        return path.equals(LlmConfig.CUSTOM_PROPERTIES)
                || path.startsWith(LlmConfig.CUSTOM_PROPERTIES + ".")
                || path.startsWith("\"" + LlmConfig.CUSTOM_PROPERTIES + "\".");
    }

    private void record(String path) {
        asked.add(prefix + path);
    }
}
