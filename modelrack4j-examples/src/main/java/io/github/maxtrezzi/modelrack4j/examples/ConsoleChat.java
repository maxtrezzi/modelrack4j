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

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.moderation.Moderation;
import io.github.maxtrezzi.modelrack4j.LlmBundle;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import io.github.maxtrezzi.modelrack4j.UnknownConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * An interactive console: pick a configured model from a menu, chat with it, {@code /menu}
 * to come back and pick another.
 *
 * <p>Run it with any configuration file — the bundled {@code council.conf} needs
 * {@code ANTHROPIC_API_KEY} and {@code OPENAI_API_KEY}, and it spends money:
 *
 * <pre>{@code
 * mvn -q -pl modelrack4j-examples exec:java \
 *     -Dexec.mainClass=io.github.maxtrezzi.modelrack4j.examples.ConsoleChat \
 *     -Dexec.args=modelrack4j-examples/src/main/resources/council.conf
 * }</pre>
 *
 * <p>Pass several files to see layering: they are applied lowest precedence first, so the
 * last one wins.
 *
 * <p><strong>Leave it running and edit the file.</strong> The registry watches its layers,
 * so adding a block makes a new entry appear in the menu, changing a temperature takes
 * effect on the next question, and removing the block you are chatting with drops you back
 * to the menu. That is the whole point of the library, and it is hard to feel from a test.
 *
 * <p>What the code shows, beyond the menu:
 *
 * <ul>
 *   <li>{@code registry.get(name)} is called <em>per turn</em>, never cached in a field. A
 *       cached bundle keeps working and silently stops reflecting the file.
 *   <li>Each of the bundle's optional parts is used when it is there and skipped when it is
 *       not: streaming, moderation on the way in, and memory across turns.
 * </ul>
 *
 * @implNote Adding a provider means adding its module to this POM as well as a block to the
 *     configuration file — core alone knows no providers.
 */
public final class ConsoleChat {

    /** Typed during a chat, returns to the menu. */
    private static final String MENU_COMMAND = "/menu";

    /** Typed anywhere, ends the session. */
    private static final String EXIT_COMMAND = "/exit";

    /**
     * The conversation this console keeps. A real application would use one id per user or
     * per session; there is exactly one conversation here.
     */
    private static final String MEMORY_ID = "console";

    /** How long to wait for a streamed answer before giving up on it. */
    private static final long STREAM_TIMEOUT_SECONDS = 120;

    private ConsoleChat() {
    }

    /**
     * Loads the configuration layers, then alternates between the menu and a chat.
     *
     * @param args one or more configuration files, lowest precedence first
     * @throws IOException if the console cannot be read
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("usage: ConsoleChat <config-file> [<higher-precedence-file>...]");
            System.exit(2);
            return;
        }

        List<Path> layers = Arrays.stream(args).map(Path::of).toList();

        try (LlmRegistry registry = LlmRegistry.builder()
                        .configFiles(layers)
                        .watch(true)
                        .build();
                BufferedReader console =
                        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            registry.onReload(change -> System.out.println(System.lineSeparator()
                    + "  [config reloaded: updated=" + change.updated()
                    + " added=" + change.added()
                    + " removed=" + change.removed() + "]"));
            registry.onReloadFailure(failure -> System.out.println(System.lineSeparator()
                    + "  [config rejected, still running the previous one: "
                    + failure.cause().getMessage() + "]"));

            System.out.println("modelrack4j console");
            System.out.println("watching " + layers + " — edit and save while this runs.");

            while (true) {
                Optional<String> chosen = chooseConfiguration(registry, console);
                if (chosen.isEmpty()) {
                    break;
                }
                if (chat(registry, chosen.get(), console) == Outcome.EXIT) {
                    break;
                }
            }
            System.out.println("bye.");
        }
    }

    /** Why a chat ended: the user asked for the menu, or asked to leave. */
    private enum Outcome {
        MENU,
        EXIT
    }

    /**
     * Prints the configured names and reads a choice.
     *
     * @return the chosen name, or empty when the user is done
     */
    private static Optional<String> chooseConfiguration(LlmRegistry registry, BufferedReader console)
            throws IOException {
        while (true) {
            // Re-read on every pass rather than once: a name may have appeared or
            // disappeared while the previous chat was running.
            List<String> names = List.copyOf(registry.names());

            System.out.println();
            System.out.println("configured models");
            for (int i = 0; i < names.size(); i++) {
                LlmBundle bundle = registry.get(names.get(i));
                System.out.printf("  %d  %-12s %s / %s%s%n",
                        i + 1,
                        names.get(i),
                        bundle.config().provider(),
                        bundle.config().modelName(),
                        capabilitiesOf(bundle));
            }
            System.out.print("choose 1-" + names.size() + " by number or name, or "
                    + EXIT_COMMAND + ": ");

            String line = console.readLine();
            if (line == null || EXIT_COMMAND.equalsIgnoreCase(line.trim())) {
                return Optional.empty();
            }
            String choice = line.trim();
            if (choice.isEmpty()) {
                continue;
            }
            if (names.contains(choice)) {
                return Optional.of(choice);
            }
            Optional<String> byNumber = byNumber(names, choice);
            if (byNumber.isPresent()) {
                return byNumber;
            }
            System.out.println("  no such configuration: " + choice);
        }
    }

    private static Optional<String> byNumber(List<String> names, String choice) {
        try {
            int index = Integer.parseInt(choice);
            return index >= 1 && index <= names.size()
                    ? Optional.of(names.get(index - 1))
                    : Optional.empty();
        } catch (NumberFormatException e) {
            // Not a number, so it was meant as a name and did not match one.
            return Optional.empty();
        }
    }

    private static String capabilitiesOf(LlmBundle bundle) {
        StringBuilder parts = new StringBuilder();
        if (bundle.streamingChatModel().isPresent()) {
            parts.append("  streaming");
        }
        if (bundle.moderationModel().isPresent()) {
            parts.append("  moderated");
        }
        bundle.config().memory().ifPresent(memory -> parts.append("  ").append(memory.typeName()));
        return parts.toString();
    }

    /** Runs one conversation until the user asks for the menu or for the exit. */
    private static Outcome chat(LlmRegistry registry, String name, BufferedReader console)
            throws IOException {
        // Built once per visit, so leaving and coming back starts a fresh conversation.
        // Empty when the configuration has no memory block, and then every turn is
        // independent — which is what that configuration says should happen.
        Optional<ChatMemory> memory;
        try {
            memory = registry.get(name).chatMemoryProvider().map(provider -> provider.get(MEMORY_ID));
        } catch (UnknownConfigurationException e) {
            // Removed between the menu being drawn and the choice being made. A narrow
            // window, but a watched file can change at any point, and the alternative here
            // is a stack trace on the way into the chat.
            System.out.println("  [" + name + " is no longer configured]");
            return Outcome.MENU;
        }

        System.out.println();
        System.out.println("chatting with " + name
                + (memory.isPresent() ? "" : " (no memory configured: each turn is independent)"));
        System.out.println(MENU_COMMAND + " for the menu, " + EXIT_COMMAND + " to quit.");

        while (true) {
            System.out.print(System.lineSeparator() + "you> ");
            String line = console.readLine();
            if (line == null || EXIT_COMMAND.equalsIgnoreCase(line.trim())) {
                return Outcome.EXIT;
            }
            String question = line.trim();
            if (MENU_COMMAND.equalsIgnoreCase(question)) {
                return Outcome.MENU;
            }
            if (question.isEmpty()) {
                continue;
            }

            LlmBundle bundle;
            try {
                // Per turn, deliberately. This is the line that makes a reload visible, and
                // caching it in a field is the one mistake that silently disables reloading.
                bundle = registry.get(name);
            } catch (UnknownConfigurationException e) {
                System.out.println("  [" + name + " is no longer configured — back to the menu]");
                return Outcome.MENU;
            }

            if (isFlagged(bundle, question)) {
                continue;
            }
            answer(bundle, memory, question);
        }
    }

    /** Moderates the question when the configuration asked for moderation. */
    private static boolean isFlagged(LlmBundle bundle, String question) {
        Optional<Moderation> moderation =
                bundle.moderationModel().map(model -> model.moderate(question).content());
        if (moderation.isPresent() && moderation.get().flagged()) {
            System.out.println("  [flagged by moderation, not sent: "
                    + moderation.get().flaggedText() + "]");
            return true;
        }
        return false;
    }

    private static void answer(LlmBundle bundle, Optional<ChatMemory> memory, String question) {
        UserMessage asked = UserMessage.from(question);
        List<ChatMessage> conversation;
        if (memory.isPresent()) {
            memory.get().add(asked);
            conversation = memory.get().messages();
        } else {
            conversation = List.of(asked);
        }

        System.out.print(bundle.name() + "> ");
        try {
            ChatResponse response;
            if (bundle.streamingChatModel().isPresent()) {
                response = streamed(bundle, conversation);   // prints as the tokens arrive
            } else {
                response = bundle.chatModel().chat(conversation);
                System.out.println(response.aiMessage().text());
            }
            memory.ifPresent(kept -> kept.add(response.aiMessage()));
        } catch (RuntimeException e) {
            // The provider failed, or the answer timed out. One bad turn should not end the
            // session, so report it and let the user ask again. The question stays in memory
            // without an answer beside it, which is honest: it was asked.
            System.out.println(System.lineSeparator() + "  [request failed: " + e + "]");
        }
    }

    /** Streams the answer to the console as it arrives, and returns the complete response. */
    private static ChatResponse streamed(LlmBundle bundle, List<ChatMessage> conversation) {
        ConsolePrinter printer = new ConsolePrinter();
        bundle.streamingChatModel().orElseThrow().chat(conversation, printer);
        return printer.awaitCompletion();
    }

    /**
     * Prints partial responses as they arrive and hands the complete one back to the calling
     * thread.
     *
     * @implNote The callbacks run on the provider's own thread, so the fields are volatile
     *     and the latch is what publishes them. A plain field would be a data race the
     *     console would usually get away with, which is the worst kind.
     */
    private static final class ConsolePrinter implements StreamingChatResponseHandler {

        private final CountDownLatch finished = new CountDownLatch(1);
        private volatile ChatResponse response;
        private volatile Throwable error;

        @Override
        public void onPartialResponse(String partial) {
            System.out.print(partial);
            System.out.flush();
        }

        @Override
        public void onCompleteResponse(ChatResponse complete) {
            this.response = complete;
            System.out.println();
            finished.countDown();
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
            finished.countDown();
        }

        ChatResponse awaitCompletion() {
            try {
                if (!finished.await(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "no answer within " + STREAM_TIMEOUT_SECONDS + "s");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for the answer", e);
            }
            if (error != null) {
                throw new IllegalStateException(error.getMessage(), error);
            }
            return response;
        }
    }
}
