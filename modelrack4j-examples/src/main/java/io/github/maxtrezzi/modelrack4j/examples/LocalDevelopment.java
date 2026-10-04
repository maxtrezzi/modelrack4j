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

import io.github.maxtrezzi.modelrack4j.ConfigValidationException;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * One production file, unchanged, and one development file above it that moves the same
 * model to a local Ollama server.
 *
 * <p>This is what layering is for. {@code production.conf} names OpenAI and reads its key from
 * {@code ${OPENAI_API_KEY}}. On a developer's machine that variable is often not set, and the
 * developer does not want to pay for every test prompt. {@code development.conf} is a few
 * lines: it changes the provider to {@code ollama}, gives the address of the local server, and
 * removes the key with {@code api-key = null}, because Ollama takes none. The application code
 * asks for {@code "ASSISTANT"} in both cases and never learns which one answered.
 *
 * <p>The development file could reach the same server through the OpenAI protocol instead:
 * {@code provider = openai} with {@code base-url = "http://localhost:11434/v1"}, and no key.
 * That is also how it would reach any other OpenAI-compatible server, such as LocalAI, vLLM or
 * LM Studio. {@code local-models.conf}, for {@code ConsoleChat} and {@code ThreeModelCouncil},
 * shows both forms.
 *
 * <p>It runs in two steps:
 *
 * <ol>
 *   <li>Both files together. The bundle is an Ollama one, with no key, and it answers one
 *       question if an Ollama server is running.
 *   <li>The production file alone, which is what the production machine loads. With
 *       {@code OPENAI_API_KEY} set it builds an OpenAI bundle, and sends it nothing; without
 *       the variable it fails, and the message names it. That failure is the reason the
 *       development file exists.
 * </ol>
 *
 * <pre>{@code
 * mvn install                                     # exec:java reads ~/.m2, not the reactor
 * mvn -q -pl modelrack4j-examples exec:java \
 *     -Dexec.mainClass=io.github.maxtrezzi.modelrack4j.examples.LocalDevelopment
 * }</pre>
 *
 * @implNote <strong>Free.</strong> It needs no key and sends no request to a paid API. The one
 *     request it makes goes to your own Ollama server, at {@code http://localhost:11434}
 *     unless {@code OLLAMA_BASE_URL} names another, with the model {@code llama3.2} unless
 *     {@code OLLAMA_MODEL} names another. Without a server, or without that model, it says so
 *     and carries on: the configuration is what it demonstrates, and that needs no server.
 */
public final class LocalDevelopment {

    private static final String NAME = "ASSISTANT";
    private static final String QUESTION = "In one short sentence: what are you?";

    private LocalDevelopment() {
    }

    /**
     * Loads both layers, then the production layer alone, and prints what each one built.
     *
     * @param args ignored
     * @throws IOException if the temporary configuration cannot be written
     */
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("modelrack4j-local");
        Path production = dir.resolve("production.conf");
        Path development = dir.resolve("development.conf");
        Files.writeString(production, productionLayer(), StandardCharsets.UTF_8);
        Files.writeString(development, developmentLayer(), StandardCharsets.UTF_8);

        System.out.println("=== 1. production.conf + development.conf: a developer's machine ===");
        System.out.println(production);
        System.out.println(development);
        try (LlmRegistry<?> registry = LlmRegistry.builder()
                .configFiles(List.of(production, development))
                .build()) {
            describe(registry.get(NAME).config());
            ask(registry);
        }

        System.out.println();
        System.out.println("=== 2. production.conf alone: the production machine ===");
        try (LlmRegistry<?> registry = LlmRegistry.builder()
                .configFiles(List.of(production))
                .build()) {
            describe(registry.get(NAME).config());
            System.out.println("(no request sent: this one would cost money)");
        } catch (ConfigValidationException e) {
            // Expected on a machine without the variable, and the point of step 1: the same
            // file loads there only because the development layer replaced the key.
            System.out.println("refused    : " + e.getMessage());
        }

        System.out.println();
        System.out.println("The application asked for \"" + NAME + "\" both times."
                + " The layers decided who answers.");
    }

    private static void describe(LlmConfig config) {
        System.out.println("provider   : " + config.provider());
        System.out.println("model      : " + config.modelName());
        System.out.println("base-url   : " + config.baseUrl().orElse("(the provider's own)"));
        System.out.println("api-key    : " + (config.apiKey().isPresent() ? "set" : "none"));
    }

    /**
     * Asks the configured model one question.
     *
     * @implNote It names no provider, like every call site in these examples. A failed request
     *     is printed rather than thrown, because the most likely cause is that no Ollama server
     *     is running, and that does not change what the example shows.
     */
    private static void ask(LlmRegistry<?> registry) {
        var bundle = registry.get(NAME);
        try {
            System.out.println("answer     : " + bundle.chatModel().chat(QUESTION));
        } catch (RuntimeException e) {
            LlmConfig config = bundle.config();
            System.out.println("answer     : [request failed: " + e.getMessage() + "]");
            System.out.println("             Is an Ollama server running at "
                    + config.baseUrl().orElse("?") + ", and has it pulled the model?");
            System.out.println("             Try: ollama pull " + config.modelName());
        }
    }

    private static String productionLayer() {
        return """
                # production.conf: what the production machine loads, and nothing else.
                llm.ASSISTANT {
                  description = "answers the application's users"
                  provider    = openai
                  api-key     = ${OPENAI_API_KEY}
                  model-name  = "gpt-5.1"
                  timeout     = 60s
                }
                """;
    }

    private static String developmentLayer() {
        // api-key = null clears the key the production layer sets, and because layers are
        // merged before anything is resolved, ${OPENAI_API_KEY} is then never read. Ollama
        // refuses a key, so without this line the block would be rejected.
        return """
                # development.conf: listed after production.conf, so it wins where they differ.
                llm.ASSISTANT {
                  provider   = ollama
                  api-key    = null
                  base-url   = "http://localhost:11434"
                  base-url   = ${?OLLAMA_BASE_URL}
                  model-name = "llama3.2"
                  model-name = ${?OLLAMA_MODEL}
                  timeout    = 120s
                }
                """;
    }
}
