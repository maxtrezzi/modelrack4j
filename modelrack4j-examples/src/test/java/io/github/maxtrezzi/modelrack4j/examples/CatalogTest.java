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
package io.github.maxtrezzi.modelrack4j.examples;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.maxtrezzi.modelrack4j.ConfigSource;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Keeps the provider catalogue true on its own: {@code catalog.conf} and every {@code hocon}
 * block of {@code docs/manual/providers.md} load and validate, and the two say the same thing.
 *
 * <p>No request is sent. Building a model does not contact its service, so this checks that
 * each block is well formed and accepted by its provider, not that the service answers; the
 * levels on the page are for that.
 */
class CatalogTest {

    /** Surefire runs in the module's directory, so both paths are relative to it. */
    private static final Path CATALOG = Path.of("src/main/resources/catalog.conf");
    private static final Path PAGE = Path.of("../docs/manual/providers.md");

    private static final Pattern SUBSTITUTION = Pattern.compile("\\$\\{\\??([A-Z_]+)}");
    private static final Pattern HOCON_BLOCK = Pattern.compile("```hocon\\n(.*?)```", Pattern.DOTALL);

    /**
     * A key every provider accepts: GLM's is the one with a required shape, {@code id.secret}
     * with a secret of at least 16 bytes.
     */
    private static final String PLACEHOLDER_KEY = "placeholder.0123456789abcdef";

    @Test
    @DisplayName("every block of catalog.conf validates with placeholder keys")
    void theCatalogLoads() throws IOException {
        Map<String, LlmConfig> catalog = load("catalog.conf", Files.readString(CATALOG));

        assertThat(catalog).containsOnlyKeys("OPENAI", "ANTHROPIC", "GEMINI", "GLM", "MISTRAL",
                "OLLAMA", "OLLAMA-OPENAI", "LOCAL-SERVER", "KIMI", "KIMI-ANTHROPIC", "DEEPSEEK",
                "GROQ", "OPENROUTER", "TOGETHER", "XAI");
    }

    @Test
    @DisplayName("every hocon block on providers.md validates, and matches catalog.conf")
    void thePageMatchesTheCatalog() throws IOException {
        Map<String, LlmConfig> catalog = load("catalog.conf", Files.readString(CATALOG));
        String page = Files.readString(PAGE);

        Map<String, LlmConfig> fromPage = new LinkedHashMap<>();
        List<String> blocks = hoconBlocks(page);
        assertThat(blocks).as("hocon blocks on %s", PAGE).isNotEmpty();
        for (int i = 0; i < blocks.size(); i++) {
            fromPage.putAll(load("providers.md block " + (i + 1), blocks.get(i)));
        }

        // Record equality on LlmConfig, the same comparison a reload uses: a value changed on
        // the page and not in the file, or the other way round, fails here.
        assertThat(fromPage).isEqualTo(catalog);
    }

    /** Loads one text with a placeholder for every variable it substitutes. */
    private static Map<String, LlmConfig> load(String id, String text) {
        try (LlmRegistry<Void> registry = LlmRegistry.builder()
                .sources(List.of(ConfigSource.of(id, text),
                        ConfigSource.of("placeholder keys", placeholders(text))))
                .build()) {
            Map<String, LlmConfig> configs = new LinkedHashMap<>();
            for (String name : registry.names()) {
                configs.put(name, registry.get(name).config());
            }
            return configs;
        }
    }

    /**
     * Defines every {@code ${VAR}} the text uses as a root key of a higher layer, which is
     * where Typesafe Config looks before it looks at the environment. So the result does not
     * depend on the keys of whoever runs the test.
     */
    private static String placeholders(String text) {
        Matcher matcher = SUBSTITUTION.matcher(text);
        TreeSet<String> names = new TreeSet<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        StringBuilder layer = new StringBuilder();
        for (String name : names) {
            layer.append(name).append(" = \"").append(PLACEHOLDER_KEY).append("\"\n");
        }
        return layer.toString();
    }

    private static List<String> hoconBlocks(String markdown) {
        List<String> blocks = new ArrayList<>();
        Matcher matcher = HOCON_BLOCK.matcher(markdown);
        while (matcher.find()) {
            blocks.add(matcher.group(1));
        }
        return blocks;
    }
}
