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
package io.github.maxtrezzi.modelrack4j.testing;

import io.github.maxtrezzi.modelrack4j.ConfigValidationException;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.spi.TokenEstimation;

/**
 * A factory that accepts exactly one model name and rejects every other one from
 * {@code validate}.
 *
 * <p>The objection is one core cannot make on the provider's behalf: which model names exist
 * is provider knowledge, and {@code validate} is the only place a factory can apply it. That
 * makes this fake the test of the SPI contract itself — if core stops calling
 * {@link #validate(LlmConfig)}, no other capability rule covers the gap.
 */
public final class FakeStrictProviderFactory extends FakeProviderFactory {

    /** The only model name this fake accepts. */
    public static final String SUPPORTED_MODEL = "supported-model";

    @Override
    public String providerId() {
        return "fake-strict";
    }

    @Override
    public TokenEstimation tokenEstimation() {
        return TokenEstimation.LOCAL;
    }

    @Override
    protected boolean supportsModeration() {
        return true;
    }

    @Override
    public void validate(LlmConfig config) {
        super.validate(config);
        if (!SUPPORTED_MODEL.equals(config.modelName())) {
            throw new ConfigValidationException("llm." + config.name() + ": provider '"
                    + config.provider() + "' does not offer model '" + config.modelName() + "'");
        }
    }
}
