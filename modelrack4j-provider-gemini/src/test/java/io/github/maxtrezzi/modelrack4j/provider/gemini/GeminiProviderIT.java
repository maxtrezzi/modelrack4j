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
package io.github.maxtrezzi.modelrack4j.provider.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * Talks to the real Gemini API, so it costs money and needs a credential.
 *
 * <p>The model name is chosen for cost, not currency: this test sends a real request, so it
 * asks for the cheap end of the range rather than the newest model. Nothing about the
 * library depends on which model it is.
 *
 * <p>Two independent guards, deliberately. Failsafe only runs {@code *IT} under
 * {@code -Pintegration}, and the annotation below skips the class when the key is absent, so
 * running the profile with only one provider configured skips the rest rather than failing.
 *
 * @implNote Unlike the other three modules, this one ships no model-name enum, so the name
 *     below could not be checked against the artifact and is the one place in this module
 *     where a live call is the only verification available.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiProviderIT {

    @TempDir
    Path dir;

    @Test
    @DisplayName("a configured bundle answers and streams, and its listeners see every call")
    void answersARealRequest() throws Exception {
        Path file = dir.resolve("it.conf");
        // The key is read by mandatory substitution, exactly as a real deployment would.
        Files.writeString(file, """
                llm {
                  IT {
                    provider   = gemini
                    api-key    = ${GEMINI_API_KEY}
                    model-name = "gemini-3.6-flash"
                    timeout    = 60s
                    streaming  = true
                  }
                }
                """, StandardCharsets.UTF_8);
        // ADR-0063: a listener registered on the registry sees each call the provider answers,
        // the request and the response, chat and streaming alike.
        List<String> events = new CopyOnWriteArrayList<>();
        ChatModelListener listener = new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext context) {
                events.add("request");
            }

            @Override
            public void onResponse(ChatModelResponseContext context) {
                events.add("response");
            }

            @Override
            public void onError(ChatModelErrorContext context) {
                events.add("error " + context.error());
            }
        };

        try (LlmRegistry<?> registry = LlmRegistry.builder()
                .configFiles(List.of(file))
                .chatModelListeners(config -> List.of(listener))
                .build()) {
            var bundle = registry.get("IT");

            assertThat(bundle.chatModel().chat("Reply with the single word: pong")).isNotBlank();
            assertThat(streamOnce(bundle.streamingChatModel().orElseThrow())).isNotBlank();
            awaitEvents(events, 4);
            assertThat(events).containsExactly("request", "response", "request", "response");
        }
    }

    /** Streams one request and returns the whole answer, failing with the stream's error. */
    private static String streamOnce(StreamingChatModel model) throws Exception {
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        model.chat("Reply with the single word: pong", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                done.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }
        });
        return done.get(180, TimeUnit.SECONDS).aiMessage().text();
    }

    /**
     * Waits, briefly, for the listeners to have seen every call. LangChain4j may report a
     * streamed response to its listeners after the handler above has completed.
     */
    private static void awaitEvents(List<String> events, int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (events.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
    }
}
