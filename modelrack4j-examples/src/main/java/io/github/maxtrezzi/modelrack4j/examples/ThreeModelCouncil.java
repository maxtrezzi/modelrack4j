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
import io.github.maxtrezzi.modelrack4j.LlmSnapshot;
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
 * <p>Note what this demonstrates about the API: the registry is asked for its models at the
 * point of use, never cached in a field. That is the habit the holder API exists to
 * encourage, and it is what makes hot reload reach this code: a bundle kept in a field
 * would keep working and would never reflect a later edit to the file.
 *
 * <p>It asks through a {@link LlmRegistry#snapshot()} taken once per question, rather than
 * with one {@code get()} per model. Those are not opposites — a snapshot is dropped at the
 * end of the round, so nothing is cached — and a council is exactly the case that needs one:
 * every model in a round must answer under the same configuration, which separate lookups
 * cannot promise.
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

    /**
     * Puts one question to every configured model, in turn.
     *
     * <p>One model failing does not end the round. The others still answer, and the tally at
     * the end says the round was incomplete — a council of three that quietly reports two
     * answers is worse than one that says so.
     */
    private static void askEveryModel(LlmRegistry registry, String question) {
        // One snapshot per round, taken at the point of use and dropped at the end of it.
        // A council is the case snapshot() exists for: asking registry.get() once per model
        // would let a reload land mid-round and have one member answer under a configuration
        // its partners never saw. Nothing watches this registry, so no reload can arrive
        // here — but this is the loop people copy, and it should be the shape that stays
        // correct when they do.
        LlmSnapshot round = registry.snapshot();
        int answered = 0;
        int failed = 0;
        for (String name : round.names()) {
            LlmBundle bundle = round.get(name);
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
            try {
                System.out.println("  answer: " + bundle.chatModel().chat(question));
                answered++;
            } catch (RuntimeException e) {
                // One member is down, the rest of the council is not. An expired key on one
                // provider used to end the whole session and throw away the answers already
                // printed above it.
                //
                // The exception type is printed, not just its message, because this is the
                // one example where several providers answer the same question: the same real
                // condition arrives here as a different type per provider, which is exactly
                // what the reference says the swap guarantee does not cover.
                System.out.println("  failed: " + e);
                failed++;
            }
        }
        if (failed > 0) {
            // Said out loud rather than left to be inferred from a missing block. A council
            // is a comparison, and a comparison quietly missing one of its terms is the kind
            // of wrong answer that reads as a right one.
            //
            // Two wordings, because one cannot be right for both cases: "compare them" has
            // nothing to refer to when every model failed, and naming how many are missing
            // has to survive any number of them. The first draft said "one is missing" and a
            // run with two dead keys printed it under "0 of 2".
            System.out.println();
            System.out.println(answered == 0
                    ? "!! no answers: all " + failed + " models failed."
                    : "!! incomplete round: " + answered + " of " + (answered + failed)
                            + " models answered. Compare them knowing the rest are missing.");
        }
    }
}
