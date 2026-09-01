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
 * <p>You type the question, on standard input, and every configured model answers it. It then
 * asks for the next one, until you type {@code /exit}. Each question costs one request per
 * model. Piped lines work too, which is how it is driven in a script:
 *
 * <pre>{@code
 * printf '%s\n' "Name one risk of caching a bundle in a field." /exit | ./run-council.sh
 * }</pre>
 *
 * <p>Note what this demonstrates about the API: the registry is asked for a bundle at the
 * point of use, never cached in a field. That is the habit the holder API exists to
 * encourage, and it is what makes hot reload reach this code: a bundle kept in a field
 * would keep working and would never reflect a later edit to the file.
 */
public final class ThreeModelCouncil {

    private static final String EXIT_COMMAND = "/exit";

    private ThreeModelCouncil() {
    }

    /**
     * Loads the configuration and asks each named model every question that is typed.
     *
     * @param args one argument: the path to a configuration file
     * @throws IOException if a question cannot be read from standard input
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
            int models = registry.names().size();
            System.out.println("Every question goes to all of them, so each one costs "
                    + models + (models == 1 ? " request." : " requests."));

            // Not closed on purpose: closing this reader would close System.in with it.
            BufferedReader console =
                    new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

            String question;
            while ((question = readQuestion(console)) != null) {
                askEveryModel(registry, question);
            }
            System.out.println("bye.");
        }
    }

    /**
     * Reads the next question, asking again when the line is empty.
     *
     * <p>Standard input rather than a command-line argument: {@code exec:java} splits
     * {@code -Dexec.args} on whitespace, which a question does not survive. There is no
     * default question, because a question that costs money should be one somebody meant to
     * ask.
     *
     * @param console standard input, wrapped once by the caller
     * @return the question, or {@code null} when the user typed {@code /exit} or standard
     *     input ended
     * @throws IOException if standard input cannot be read
     */
    private static String readQuestion(BufferedReader console) throws IOException {
        while (true) {
            System.out.println();
            System.out.print("question, or " + EXIT_COMMAND + " to quit: ");
            System.out.flush();

            String line = console.readLine();
            if (line == null) {
                return null;
            }
            String question = line.trim();
            if (EXIT_COMMAND.equalsIgnoreCase(question)) {
                return null;
            }
            if (!question.isEmpty()) {
                return question;
            }
        }
    }

    /** Puts one question to every configured model, in turn. */
    private static void askEveryModel(LlmRegistry registry, String question) {
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
