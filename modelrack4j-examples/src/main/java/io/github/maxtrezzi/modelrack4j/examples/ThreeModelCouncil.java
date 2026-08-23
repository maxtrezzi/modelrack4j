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
 * mvn -q -pl modelrack4j-examples exec:java \
 *     -Dexec.mainClass=io.github.maxtrezzi.modelrack4j.examples.ThreeModelCouncil \
 *     -Dexec.args=modelrack4j-examples/src/main/resources/council.conf
 * }</pre>
 *
 * <p>Note what this demonstrates about the API: the registry is asked for a bundle at the
 * point of use, never cached in a field. That is the habit the holder API exists to
 * encourage, and it is what will make hot reload work when it arrives.
 */
public final class ThreeModelCouncil {

    private static final String PROMPT =
            "In one sentence: why do layered configuration files resolve after merging?";

    private ThreeModelCouncil() {
    }

    /**
     * Loads the configuration and asks each named model the same question.
     *
     * @param args one argument: the path to a configuration file
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: ThreeModelCouncil <config-file>");
            System.exit(2);
            return;
        }

        try (LlmRegistry registry = LlmRegistry.builder()
                .configFiles(List.of(Path.of(args[0])))
                .build()) {

            System.out.println("configured names: " + registry.names());

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
                System.out.println("  answer: " + bundle.chatModel().chat(PROMPT));
            }
        }
    }
}
