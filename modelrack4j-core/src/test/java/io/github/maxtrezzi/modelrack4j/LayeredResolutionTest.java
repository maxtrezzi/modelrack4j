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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests for the layering trap: every layer is parsed without resolution, merged,
 * and resolved exactly once at the end.
 *
 * <p>Resolving each file as it is parsed is the natural-looking mistake, and it fails only in
 * layered setups — which is why this has dedicated tests rather than relying on the registry
 * suite to notice.
 */
class LayeredResolutionTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("a mandatory substitution in a lower layer is fine when a higher layer overrides that key")
    void higherLayerOverridesUnresolvableLowerLayerValue() throws IOException {
        // MODELRACK4J_ABSENT_VAR is deliberately not set in the environment. Resolving this
        // layer on its own would throw; resolving after the merge never sees it, because the
        // customer layer has replaced the whole key.
        Path defaults = write("defaults.conf", """
                llm {
                  SL {
                    provider = fake-local
                    api-key = ${MODELRACK4J_ABSENT_VAR}
                    model-name = "default-model"
                  }
                }
                """);
        Path customer = write("customer.conf", """
                llm {
                  SL {
                    api-key = "supplied-by-customer"
                    model-name = "customer-model"
                  }
                }
                """);

        var registry = LlmRegistry.builder().configFiles(List.of(defaults, customer)).build();

        assertThat(registry.get("SL").config().apiKey()).isEqualTo("supplied-by-customer");
        assertThat(registry.get("SL").config().modelName()).isEqualTo("customer-model");
    }

    @Test
    @DisplayName("a mandatory substitution unresolved after merging fails with a message naming the fix")
    void unresolvedAfterMergingFails() throws IOException {
        Path only = write("only.conf", """
                llm {
                  SL {
                    provider = fake-local
                    api-key = ${MODELRACK4J_ABSENT_VAR}
                    model-name = "m"
                  }
                }
                """);

        assertThatThrownBy(() -> LlmRegistry.builder().configFiles(List.of(only)).build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("mandatory substitution")
                .hasMessageContaining("higher-precedence layer");
    }

    @Test
    @DisplayName("a value in a lower layer can reference a key that only a higher layer defines")
    void crossLayerReferenceSeesMergedValues() throws IOException {
        Path defaults = write("defaults.conf", """
                shared { model = "unset" }
                llm {
                  SL {
                    provider = fake-local
                    api-key = "k"
                    model-name = ${shared.model}
                  }
                }
                """);
        Path customer = write("customer.conf", """
                shared { model = "defined-higher-up" }
                """);

        var registry = LlmRegistry.builder().configFiles(List.of(defaults, customer)).build();

        assertThat(registry.get("SL").config().modelName()).isEqualTo("defined-higher-up");
    }

    @Test
    @DisplayName("later files win on conflict")
    void precedenceIsLowestFirst() throws IOException {
        Path low = write("low.conf", """
                llm { SL { provider = fake-local, api-key = "k", model-name = "low", temperature = 0.1 } }
                """);
        Path high = write("high.conf", """
                llm { SL { temperature = 0.9 } }
                """);

        var registry = LlmRegistry.builder().configFiles(List.of(low, high)).build();

        assertThat(registry.get("SL").config().temperature()).contains(0.9);
    }

    @Test
    @DisplayName("a higher layer clears a lower layer's description with null")
    void nullClearsAValueSetLowerDown() throws IOException {
        // The error message for a blank description tells the user to do this, so the
        // mechanism it points at has to actually work: HOCON's null removes the key, and
        // `hasPath` is then false rather than the value being present and empty.
        Path low = write("low.conf", """
                llm { SL { provider = fake-local, api-key = "k", model-name = "m"
                           description = "inherited from the defaults layer" } }
                """);
        Path high = write("high.conf", """
                llm { SL { description = null } }
                """);

        var registry = LlmRegistry.builder().configFiles(List.of(low, high)).build();

        assertThat(registry.get("SL").config().description()).isEmpty();
        assertThat(LlmRegistry.builder().configFiles(List.of(low)).build()
                        .get("SL").config().description())
                .contains("inherited from the defaults layer");
    }

    @Test
    @DisplayName("an unreadable layer is reported by path")
    void missingFileIsReported() {
        Path missing = dir.resolve("nope.conf");

        assertThatThrownBy(() -> LlmRegistry.builder().configFiles(List.of(missing)).build())
                .isInstanceOf(ConfigAccessException.class)
                .hasMessageContaining("nope.conf");
    }

    @Test
    @DisplayName("config files are read as UTF-8 regardless of the platform default charset")
    void configFilesAreReadAsUtf8() throws IOException {
        // Java 17 predates JEP 400, so the platform default charset still follows the
        // locale. HOCON mandates UTF-8, and this pins that the library actually gets it:
        // the bytes are written as UTF-8 explicitly and must survive the round trip.
        Path file = dir.resolve("utf8.conf");
        Files.write(file, ("llm { SL { provider = fake-local, api-key = \"k\""
                + ", model-name = \"modèle-ünïcode-模型\" } }").getBytes(StandardCharsets.UTF_8));

        var registry = LlmRegistry.builder().configFiles(List.of(file)).build();

        assertThat(registry.get("SL").config().modelName()).isEqualTo("modèle-ünïcode-模型");
    }

    private Path write(String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
