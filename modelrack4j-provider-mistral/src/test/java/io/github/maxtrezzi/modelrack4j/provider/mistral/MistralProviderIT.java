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
package io.github.maxtrezzi.modelrack4j.provider.mistral;

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
 * Talks to the real Mistral API, so it costs money and needs a credential.
 *
 * <p>The model name is chosen for cost, not currency: this test sends a real request, so it
 * asks for the cheap end of the range. It also moderates once, because the moderation model
 * name is the one value this factory supplies itself, and only a live call shows that the
 * endpoint still accepts it.
 *
 * <p>Two independent guards, deliberately. Failsafe only runs {@code *IT} under
 * {@code -Pintegration}, and the annotation below skips the class when the key is absent, so
 * running the profile with only one provider configured skips the rest rather than failing.
 */
@EnabledIfEnvironmentVariable(named = "MISTRAL_API_KEY", matches = ".+")
class MistralProviderIT {

    @TempDir
    Path dir;

    @Test
    @DisplayName("a configured bundle answers a real request, and moderates one")
    void answersARealRequest() throws IOException {
        Path file = dir.resolve("it.conf");
        // The key is read by mandatory substitution, exactly as a real deployment would.
        Files.writeString(file, """
                llm {
                  IT {
                    provider   = mistral
                    api-key    = ${MISTRAL_API_KEY}
                    model-name = "mistral-small-latest"
                    timeout    = 60s
                    moderation { enabled = true }
                  }
                }
                """, StandardCharsets.UTF_8);

        try (LlmRegistry<?> registry = LlmRegistry.builder().configFiles(List.of(file)).build()) {
            var bundle = registry.get("IT");

            assertThat(bundle.chatModel().chat("Reply with the single word: pong")).isNotBlank();
            assertThat(bundle.moderationModel().orElseThrow().moderate("Have a nice day.")
                    .content().flagged()).isFalse();
        }
    }
}
