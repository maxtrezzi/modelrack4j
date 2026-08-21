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

/**
 * How a provider counts tokens, and at what cost.
 *
 * <p>The distinction is not cosmetic. {@code TokenWindowChatMemory} calls the estimator on
 * eviction, so on a {@link #REMOTE} provider ordinary conversation turns make a billed,
 * rate-limited network call inside what an application reasonably treats as local
 * bookkeeping. A boolean "has an estimator" cannot express that, because every provider that
 * has one would answer {@code true}.
 */
public enum TokenEstimation {

    /** No {@code TokenCountEstimator} at all. Token-window memory is impossible. */
    ABSENT,

    /** Counts in-process, with no network call and no per-use cost. */
    LOCAL,

    /**
     * Counts by calling the provider's API. Each estimate is a billed, rate-limited request
     * that can fail or time out. Permitted only with an explicit opt-in in configuration.
     */
    REMOTE
}
