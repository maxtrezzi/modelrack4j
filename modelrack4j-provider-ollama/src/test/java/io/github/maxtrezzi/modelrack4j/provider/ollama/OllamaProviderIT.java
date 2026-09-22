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
package io.github.maxtrezzi.modelrack4j.provider.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * Talks to a real Ollama server. It costs nothing, but it needs two things the other
 * integration tests do not: a running server, and a model already pulled onto it.
 *
 * <p>The server's address comes from {@code OLLAMA_BASE_URL}, for example
 * {@code http://localhost:11434}, and this class is skipped when it is not set, the way the
 * others are skipped without their key. The model is {@code llama3.2} unless
 * {@code OLLAMA_MODEL} names another; pull it first with {@code ollama pull llama3.2}. A
 * missing model is not skipped: it fails, with Ollama's own message, because a server that is
 * configured but cannot answer is exactly what this test exists to report.
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_BASE_URL", matches = ".+")
class OllamaProviderIT {

    @TempDir
    Path dir;

    @Test
    @DisplayName("a configured bundle answers a real request")
    void answersARealRequest() throws IOException {
        Path file = dir.resolve("it.conf");
        // The address is read by mandatory substitution, as a real deployment would read it.
        Files.writeString(file, """
                llm {
                  IT {
                    provider   = ollama
                    base-url   = ${OLLAMA_BASE_URL}
                    model-name = "llama3.2"
                    model-name = ${?OLLAMA_MODEL}
                    timeout    = 120s
                  }
                }
                """, StandardCharsets.UTF_8);

        try (LlmRegistry<?> registry = LlmRegistry.builder().configFiles(List.of(file)).build()) {
            String answer =
                    registry.get("IT").chatModel().chat("Reply with the single word: pong");

            assertThat(answer).isNotBlank();
        }
    }
}
