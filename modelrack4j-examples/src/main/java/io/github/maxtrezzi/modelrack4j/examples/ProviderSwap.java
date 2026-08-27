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

import io.github.maxtrezzi.modelrack4j.LlmBundle;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Changes the provider of a running application by editing a file, and proves it by asking
 * the same question twice through the same call site.
 *
 * <p>LangChain4j is already provider-agnostic where it counts — {@code ChatModel} is an
 * interface. What is not agnostic is <em>choosing</em>: picking a provider and a model is a
 * constructor call, so it is code, and changing it is a redeploy. This example moves that
 * decision into a file and then changes it while the process runs.
 *
 * <p>Watch what the output does <strong>not</strong> contain: any branch on the provider
 * name. {@link #ask} is one method, called twice, and it never learns who answered.
 *
 * <pre>{@code
 * mvn install                                     # exec:java reads ~/.m2, not the reactor
 * mvn -q -pl modelrack4j-examples exec:java \
 *     -Dexec.mainClass=io.github.maxtrezzi.modelrack4j.examples.ProviderSwap
 * }</pre>
 *
 * @implNote Needs {@code ANTHROPIC_API_KEY} and {@code OPENAI_API_KEY}, and sends two real
 *     requests, so it costs a little money. Without both keys it explains itself and exits
 *     rather than failing.
 */
public final class ProviderSwap {

    private static final String NAME = "SWAP";
    private static final String QUESTION = "In one short sentence: what are you?";
    private static final Duration DEBOUNCE = Duration.ofMillis(100);
    private static final long RELOAD_TIMEOUT_MILLIS = 10_000;

    private ProviderSwap() {
    }

    /**
     * Asks, swaps the provider by rewriting the file, and asks again.
     *
     * @param args ignored
     * @throws IOException if the temporary configuration cannot be written
     * @throws InterruptedException if waiting for the reload is interrupted
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (System.getenv("ANTHROPIC_API_KEY") == null || System.getenv("OPENAI_API_KEY") == null) {
            System.err.println("""
                    This example sends two real requests and needs both keys:

                        export ANTHROPIC_API_KEY=...
                        export OPENAI_API_KEY=...

                    For a demonstration that needs no key and costs nothing, run AtomicSnapshot.""");
            return;
        }

        Path config = Files.createTempDirectory("modelrack4j-swap").resolve("llm.conf");
        Files.writeString(config, anthropic(), StandardCharsets.UTF_8);
        System.out.println("config: " + config);

        try (LlmRegistry registry = LlmRegistry.builder()
                .configFiles(List.of(config))
                .watch(true)
                .debounce(DEBOUNCE)
                .build()) {

            ask(registry);

            System.out.println();
            System.out.println("--- editing the file: provider anthropic -> openai ---");
            System.out.println("--- no recompile, no restart, no code changed        ---");
            Files.writeString(config, openAi(), StandardCharsets.UTF_8);
            awaitProvider(registry, "openai");

            ask(registry);

            System.out.println();
            System.out.println("Same method, same call site, same registry. The file decided.");
        }
    }

    /**
     * Asks the configured model the question and prints who answered.
     *
     * @param registry the registry to ask
     * @implNote This method is the point of the example: it names no provider, imports no
     *     provider type, and has no branch. It cannot tell which provider it just used.
     */
    private static void ask(LlmRegistry registry) {
        LlmBundle bundle = registry.get(NAME);
        System.out.println();
        System.out.println("provider   : " + bundle.config().provider());
        System.out.println("model      : " + bundle.config().modelName());
        System.out.println("implementation: " + bundle.chatModel().getClass().getSimpleName());
        try {
            System.out.println("answer     : " + bundle.chatModel().chat(QUESTION));
        } catch (RuntimeException e) {
            // A rejected key should not hide the thing being demonstrated: the swap below
            // still happens, and the provider still changes, request or no request.
            System.out.println("answer     : [request failed: " + e.getMessage() + "]");
        }
    }

    /** Waits for the watcher to publish a snapshot whose provider is the expected one. */
    private static void awaitProvider(LlmRegistry registry, String expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofMillis(RELOAD_TIMEOUT_MILLIS).toNanos();
        while (System.nanoTime() - deadline < 0) {
            if (expected.equals(registry.get(NAME).config().provider())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("the reload did not arrive within "
                + RELOAD_TIMEOUT_MILLIS + " ms");
    }

    private static String anthropic() {
        // No temperature: claude-sonnet-5's adaptive thinking controls its own sampling, and
        // the API rejects a non-default temperature with a 400.
        return """
                llm.SWAP {
                  description = "the model this application talks to"
                  provider    = anthropic
                  api-key     = ${ANTHROPIC_API_KEY}
                  model-name  = "claude-sonnet-5"
                }
                """;
    }

    private static String openAi() {
        // The ONLY difference. Everything else in the application is untouched.
        return """
                llm.SWAP {
                  description = "the model this application talks to"
                  provider    = openai
                  api-key     = ${OPENAI_API_KEY}
                  model-name  = "gpt-5.1"
                  temperature = 0.2
                }
                """;
    }
}
