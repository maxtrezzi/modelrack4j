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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs the three-model scenario — {@code SL}, {@code SH}, {@code CR} — against the real
 * provider APIs.
 *
 * <p>Needs {@code ANTHROPIC_API_KEY} and {@code OPENAI_API_KEY} in the environment, and it
 * spends money. Run it with the bundled configuration:
 *
 * <pre>{@code
 * mvn install                                     # exec:java reads ~/.m2, not the reactor
 * mvn -q -pl modelrack4j-examples exec:java \
 *     -Dexec.mainClass=io.github.maxtrezzi.modelrack4j.examples.ThreeModelCouncil \
 *     -Dexec.args=modelrack4j-examples/src/main/resources/examples.conf
 * }</pre>
 *
 * <p>It asks you for the question first, on standard input, and uses a default one if you
 * press Enter. A piped line works too, which is how it is driven in a script:
 *
 * <pre>{@code
 * echo "Name one risk of caching a bundle in a field." | ./run-council.sh
 * }</pre>
 *
 * <p>Note what this demonstrates about the API: the registry is asked for a bundle at the
 * point of use, never cached in a field. That is the habit the holder API exists to
 * encourage, and it is what makes hot reload reach this code: a bundle kept in a field
 * would keep working and would never reflect a later edit to the file.
 */
public final class ThreeModelCouncil {

    private static final String DEFAULT_QUESTION =
            "In one sentence: why do layered configuration files resolve after merging?";

    private ThreeModelCouncil() {
    }

    /**
     * Loads the configuration and asks each named model the same question.
     *
     * @param args one argument: the path to a configuration file
     * @throws IOException if the question cannot be read from standard input
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: ThreeModelCouncil <config-file>");
            System.exit(2);
            return;
        }

        try (LlmRegistry registry = LlmRegistry.builder()
                .configFiles(List.of(Path.of(args[0])))
                .build()) {

            System.out.println("configured names: " + registry.names());

            // Asked after the configuration has loaded, so a file that does not parse costs
            // nobody a typed question, and before the first request, so every model gets the
            // same one.
            String question = readQuestion();

            for (String name : registry.names()) {
                // Asked for on every use, deliberately. See the class javadoc.
                LlmBundle bundle = registry.get(name);
                System.out.println();
                System.out.println("=== " + name + " (" + bundle.config().provider()
                        + " / " + bundle.config().modelName() + ") ===");
                bundle.config().description()
                        .ifPresent(description -> System.out.println("  " + description));
                System.out.println("  streaming available: "
                        + bundle.streamingChatModel().isPresent());
                System.out.println("  moderation available: "
                        + bundle.moderationModel().isPresent());
                System.out.println("  memory configured: "
                        + bundle.chatMemoryProvider().isPresent());
                System.out.println("  answer: " + bundle.chatModel().chat(question));
            }
        }
    }

    /**
     * Reads the question every model will be asked, or returns the default one.
     *
     * <p>Standard input rather than a second command-line argument: {@code exec:java} splits
     * {@code -Dexec.args} on whitespace, which a question does not survive.
     *
     * @return the line that was typed or piped in, or {@link #DEFAULT_QUESTION} when it is
     *     empty or standard input has already ended
     * @throws IOException if standard input cannot be read
     */
    private static String readQuestion() throws IOException {
        System.out.println();
        System.out.println("The question every configured model will be asked.");
        System.out.println("Press Enter for: " + DEFAULT_QUESTION);
        System.out.print("> ");
        System.out.flush();

        // Not closed on purpose: closing this reader would close System.in with it.
        BufferedReader console =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line = console.readLine();
        String question = line == null ? "" : line.trim();
        if (question.isEmpty()) {
            System.out.println("(using the default question)");
            return DEFAULT_QUESTION;
        }
        return question;
    }
}
