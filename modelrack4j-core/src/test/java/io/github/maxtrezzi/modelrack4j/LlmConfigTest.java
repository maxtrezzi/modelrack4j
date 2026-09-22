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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
        // Every required key, and assertAll so one broken key does not hide the others.
        // Checking a single key left `name` and `provider` unverified: dropping either check
        // still built a configuration, and a blank provider then failed much later with an
        // unrelated message about no provider module being on the classpath.
        assertAll(
                () -> assertRejectsBlank(config().withName(blank), ".name is required"),
                () -> assertRejectsBlank(config().withProvider(blank), "provider is required"),
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
    @DisplayName("a present but blank api-key or base-url is rejected, naming the key")
    void blankOptionalConnectionValuesAreRejected(String blank) {
        // Optional since ADR-0062, and still not blank: a blank key would reach the provider
        // as an empty credential, and a blank address as one with no host.
        assertAll(
                () -> assertRejectsBlank(config().withApiKey(blank),
                        "api-key is present but blank"),
                () -> assertRejectsBlank(config().withBaseUrl(blank),
                        "base-url is present but blank"));
    }

    @Test
    @DisplayName("a block may leave out api-key; whether it must set one is the provider's rule")
    void apiKeyIsOptionalInTheRecord() {
        LlmConfig config = LlmConfig.fromBlock("SL", block(""));

        assertThat(config.apiKey()).isEmpty();
        assertThat(config.baseUrl()).isEmpty();
    }

    @Test
    @DisplayName("base-url is read from the block and is not reported as an unknown key")
    void baseUrlIsAKnownKey() {
        // Known because the parse asks for it (ADR-0056): if the read went away, this block
        // would be refused as carrying a key this library does not know.
        LlmConfig config = LlmConfig.fromBlock("SL",
                block("base-url = \"http://localhost:11434\"\n"));

        assertThat(config.baseUrl()).contains("http://localhost:11434");
    }

    @Test
    @DisplayName("changing only base-url makes it a different configuration")
    void baseUrlParticipatesInEquality() {
        // A different address is a different server, so the reload diff has to rebuild the
        // bundle (ADR-0006). equals is the record's own, as for the key.
        assertThat(config().withBaseUrl("http://a:1").build())
                .isNotEqualTo(config().withBaseUrl("http://b:1").build());
        assertThat(config().withBaseUrl("http://a:1").build())
                .isEqualTo(config().withBaseUrl("http://a:1").build());
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
    @DisplayName("a substituted secret in a custom property does not reach toString()")
    void toStringRedactsACustomPropertyToo() {
        // A substitution resolves inside the sub-block, so a custom property holds the
        // credential after substitution exactly as api-key does: ADR-0047 a second time.
        LlmConfig config = LlmConfig.fromBlock("SL", ConfigFactory.parseString(
                "provider = fake-local\n"
                        + "api-key = \"k\"\n"
                        + "model-name = \"m\"\n"
                        + "timeout = 60s\n"
                        + "log-requests = false\n"
                        + "log-responses = false\n"
                        + "streaming = false\n"
                        + "custom-properties { webhook-token = \"shhh-1234\","
                        + " prompt-id = \"p1\" }\n"));

        String described = config.toString();

        assertThat(described).doesNotContain("shhh-1234");
        // Nor the key names: reading them would mean parsing a text this library has just
        // promised not to interpret.
        assertThat(described).doesNotContain("webhook-token", "prompt-id");
        assertThat(described).contains("customPropertiesText=***");
    }

    @Test
    @DisplayName("a configuration with no custom properties says so rather than hiding it")
    void toStringShowsAnEmptyCustomPropertiesBlock() {
        assertThat(config().build().toString()).contains("customPropertiesText={}");
    }

    @Test
    @DisplayName("toString hides the credential but keeps every other component")
    void toStringRedactsTheApiKey() {
        LlmConfig config = config()
                .withApiKey("sk-not-a-real-key-12345")
                .withModelName("gpt-5.1")
                .build();

        String described = config.toString();

        assertThat(described).doesNotContain("sk-not-a-real-key-12345");
        assertThat(described).contains("apiKey=***");
        // The rest must survive: a description with the useful half removed is not a fix,
        // it is a second problem. These are the components a reader actually needs.
        assertThat(described).contains("name=SL", "provider=fake-local", "modelName=gpt-5.1");

        // Every component, asked of the record rather than listed here. toString() is
        // hand-written to redact the key, so a component added to LlmConfig would compile,
        // print nowhere, and be caught by nothing. This fails on that day instead.
        for (RecordComponent component : LlmConfig.class.getRecordComponents()) {
            assertThat(described)
                    .as("toString() omits the component '%s'", component.getName())
                    .contains(component.getName() + "=");
        }
    }

    @Test
    @DisplayName("toString says a key is absent, rather than printing a mask over nothing")
    void toStringShowsAnAbsentKey() {
        assertThat(config().withApiKey(null).build().toString())
                .contains("apiKey=Optional.empty")
                .doesNotContain("apiKey=***");
    }

    @Test
    @DisplayName("toString prints base-url with its user-info hidden")
    void toStringRedactsTheUserInfoOfABaseUrl() {
        String described = config()
                .withBaseUrl("https://proxy-user:s3cret@gateway.example:8443/v1")
                .build()
                .toString();

        assertThat(described).doesNotContain("proxy-user", "s3cret");
        // The address itself survives: which server a block calls is what a log reader needs.
        assertThat(described).contains("baseUrl=Optional[https://***@gateway.example:8443/v1]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:11434", "localhost:11434/v1", ""})
    @DisplayName("a base-url with no @ is printed unchanged")
    void anAddressWithoutUserInfoIsUnchanged(String url) {
        assertThat(LlmConfig.withoutUserInfo(url)).isEqualTo(url);
    }

    @ParameterizedTest
    @CsvSource({
        "user:pw@host:1/path, ***@host:1/path",
        "https://user:pw@host/v1, https://***@host/v1",
        // A password written with an unencoded /, ? or # ends the authority early by the
        // rules. Stopping the search there printed the rest of the password.
        "https://user:pa/ss@host, https://***@host",
        "https://user:pa?ss@host, https://***@host",
        "https://user:pa#ss@host, https://***@host",
        "https://user:p@ss@host, https://***@host",
        // An @ in a path or a query hides the host too: hiding too much is the safe side.
        "http://host/api?user=a@b, http://***@b",
        // Empty user-info is still replaced, so nothing depends on what it held.
        "http://@host, http://***@host",
        // A scheme separator at the very start still ends the scheme.
        "://user:pw@host, ://***@host",
    })
    @DisplayName("everything between the scheme and the last @ is replaced")
    void userInfoIsReplaced(String url, String printed) {
        assertThat(LlmConfig.withoutUserInfo(url)).isEqualTo(printed);
    }

    @Test
    @DisplayName("redacting toString leaves the reload diff able to see a changed key")
    void redactionDoesNotWeakenEquality() {
        // ADR-0006 diffs configurations by record equality, so two blocks differing only in
        // their key must stay different. Overriding toString must not have reached equals:
        // if it had, a rotated credential would be a configuration that never reloaded.
        LlmConfig first = config().withApiKey("first-key").build();
        LlmConfig second = config().withApiKey("second-key").build();

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isEqualTo(config().withApiKey("first-key").build());
        assertThat(first).hasSameHashCodeAs(config().withApiKey("first-key").build());
        assertThat(first.toString()).isEqualTo(second.toString());
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

    /** A block with every key but {@code api-key}, followed by {@code extra}. */
    private static Config block(String extra) {
        return ConfigFactory.parseString("provider = fake-local\n"
                + "model-name = \"m\"\n"
                + "timeout = 60s\n"
                + "log-requests = false\n"
                + "log-responses = false\n"
                + "streaming = false\n"
                + extra);
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
        private Optional<String> apiKey = Optional.of("key");
        private Optional<String> baseUrl = Optional.empty();
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
            this.apiKey = Optional.ofNullable(value);
            return this;
        }

        Fixture withBaseUrl(String value) {
            this.baseUrl = Optional.ofNullable(value);
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
            return new LlmConfig(name, description, provider, apiKey, baseUrl, modelName,
                    temperature, timeout, false, false, false, Optional.empty(), false, "{}");
        }
    }
}
