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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Validation lives in the record's constructor, so an {@link LlmConfig} that exists is valid.
 * These tests pin that, and pin the value equality that per-name reload diffing depends on.
 */
class LlmConfigTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("a blank required value is rejected, naming the key")
    void blankRequiredValuesAreRejected(String blank) {
        assertThatThrownBy(() -> config().withApiKey(blank).build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("api-key");
    }

    @Test
    @DisplayName("a null required value is rejected rather than reaching a model builder")
    void nullRequiredValuesAreRejected() {
        assertThatThrownBy(() -> config().withModelName(null).build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("model-name");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 2.1})
    @DisplayName("a temperature no provider accepts is rejected")
    void temperatureOutOfRangeIsRejected(double temperature) {
        assertThatThrownBy(() -> config().withTemperature(temperature).build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("temperature");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0, 2.0})
    @DisplayName("the accepted temperature range is inclusive at both ends")
    void temperatureBoundsAreInclusive(double temperature) {
        assertThat(config().withTemperature(temperature).build().temperature())
                .contains(temperature);
    }

    @Test
    @DisplayName("a non-positive timeout is rejected")
    void nonPositiveTimeoutIsRejected() {
        assertThatThrownBy(() -> config().withTimeout(Duration.ZERO).build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    @DisplayName("memory bounds must be positive")
    void memoryBoundsMustBePositive() {
        assertThatThrownBy(() -> new MemoryConfig.MessageWindow(0))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("max-messages");
        assertThatThrownBy(() -> new MemoryConfig.TokenWindow(-1, false))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("max-tokens");
    }

    @Test
    @DisplayName("equal values are equal configs — the basis of per-name reload diffing")
    void valueEqualityHolds() {
        assertThat(config().build()).isEqualTo(config().build());
        assertThat(config().build()).hasSameHashCodeAs(config().build());
        assertThat(config().withTemperature(0.5).build()).isNotEqualTo(config().build());
    }

    @Test
    @DisplayName("memory variants are distinguished by value, not identity")
    void memoryVariantsCompareByValue() {
        assertThat(new MemoryConfig.MessageWindow(10))
                .isEqualTo(new MemoryConfig.MessageWindow(10))
                .isNotEqualTo(new MemoryConfig.MessageWindow(11));
        assertThat(new MemoryConfig.TokenWindow(10, true))
                .isNotEqualTo(new MemoryConfig.TokenWindow(10, false));
    }

    @Test
    @DisplayName("each memory variant reports its discriminator value")
    void variantsReportTheirTypeName() {
        assertThat(new MemoryConfig.MessageWindow(1).typeName()).isEqualTo("message-window");
        assertThat(new MemoryConfig.TokenWindow(1, false).typeName()).isEqualTo("token-window");
    }

    private static Fixture config() {
        return new Fixture();
    }

    /** A valid configuration that each test bends in exactly one direction. */
    private static final class Fixture {
        private String apiKey = "key";
        private String modelName = "model";
        private Optional<Double> temperature = Optional.empty();
        private Duration timeout = Duration.ofSeconds(60);

        Fixture withApiKey(String value) {
            this.apiKey = value;
            return this;
        }

        Fixture withModelName(String value) {
            this.modelName = value;
            return this;
        }

        Fixture withTemperature(double value) {
            this.temperature = Optional.of(value);
            return this;
        }

        Fixture withTimeout(Duration value) {
            this.timeout = value;
            return this;
        }

        LlmConfig build() {
            return new LlmConfig("SL", "fake-local", apiKey, modelName, temperature, timeout,
                    false, false, false, Optional.empty(), false);
        }
    }
}
