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
import static org.junit.jupiter.api.Assertions.assertAll;

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
        // All four keys, and assertAll so one broken key does not hide the other three.
        // Checking a single key left `name` and `provider` unverified: dropping either check
        // still built a configuration, and a blank provider then failed much later with an
        // unrelated message about no provider module being on the classpath.
        assertAll(
                () -> assertRejectsBlank(config().withName(blank), ".name is required"),
                () -> assertRejectsBlank(config().withProvider(blank), "provider is required"),
                () -> assertRejectsBlank(config().withApiKey(blank), "api-key is required"),
                () -> assertRejectsBlank(config().withModelName(blank), "model-name is required"));
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

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("memory bounds must be positive")
    void memoryBoundsMustBePositive(int bound) {
        // The bound is a parameter so that both variants are checked against the same values.
        // Written out, the two branches looked symmetrical while testing 0 against one
        // variant and -1 against the other, which left the boundary itself unverified on
        // token-window: accepting max-tokens = 0 broke no test.
        assertAll(
                () -> assertThatThrownBy(() -> new MemoryConfig.MessageWindow(bound))
                        .isInstanceOf(ConfigValidationException.class)
                        .hasMessageContaining("max-messages"),
                () -> assertThatThrownBy(() -> new MemoryConfig.TokenWindow(bound, false))
                        .isInstanceOf(ConfigValidationException.class)
                        .hasMessageContaining("max-tokens"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("a present but blank description is rejected, and the message says how to clear one")
    void blankDescriptionIsRejected(String blank) {
        // The message is part of the contract: `description = null` is the documented way for
        // a higher layer to remove a description a lower layer set, and nothing else says so
        // at the point of failure.
        assertThatThrownBy(() -> config().withDescription(blank).build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("description")
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("a description is optional and carried through when present")
    void descriptionIsOptional() {
        assertThat(config().build().description()).isEmpty();
        assertThat(config().withDescription("the cheap one").build().description())
                .contains("the cheap one");
    }

    @Test
    @DisplayName("changing only the description makes it a different configuration")
    void descriptionParticipatesInEquality() {
        // ADR-0032: the diff is record equality, and the description is part of the record, so
        // editing it rebuilds that bundle. Cheap, and it keeps ADR-0006's rule to one sentence.
        assertThat(config().withDescription("first").build())
                .isNotEqualTo(config().withDescription("second").build());
        assertThat(config().withDescription("same").build())
                .isEqualTo(config().withDescription("same").build());
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

    private static void assertRejectsBlank(Fixture fixture, String expectedFragment) {
        assertThatThrownBy(fixture::build)
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining(expectedFragment);
    }

    /** A valid configuration that each test bends in exactly one direction. */
    private static final class Fixture {
        private String name = "SL";
        private Optional<String> description = Optional.empty();
        private String provider = "fake-local";
        private String apiKey = "key";
        private String modelName = "model";
        private Optional<Double> temperature = Optional.empty();
        private Duration timeout = Duration.ofSeconds(60);

        Fixture withName(String value) {
            this.name = value;
            return this;
        }

        Fixture withDescription(String value) {
            this.description = Optional.ofNullable(value);
            return this;
        }

        Fixture withProvider(String value) {
            this.provider = value;
            return this;
        }

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
            return new LlmConfig(name, description, provider, apiKey, modelName, temperature,
                    timeout, false, false, false, Optional.empty(), false);
        }
    }
}
