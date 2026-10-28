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
package io.github.maxtrezzi.modelrack4j.provider.ollama;

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
 * Talks to a real Ollama server. It costs nothing, but it needs two things the other
 * integration tests do not: a running server, and a model already pulled onto it.
 *
 * <p>The server's address comes from {@code OLLAMA_BASE_URL}, for example
 * {@code http://localhost:11434}, and this class is skipped when it is not set, the way the
 * others are skipped without their key. The model is {@code llama3.2} unless
 * {@code OLLAMA_MODEL} names another; pull it first with {@code ollama pull llama3.2}. A
 * missing model is not skipped: it fails, with Ollama's own message, because a server that is
 * configured but cannot answer is exactly what this test exists to report.
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_BASE_URL", matches = ".+")
class OllamaProviderIT {

    @TempDir
    Path dir;

    @Test
    @DisplayName("a configured bundle answers and streams, and its listeners see every call")
    void answersARealRequest() throws Exception {
        Path file = dir.resolve("it.conf");
        // The address is read by mandatory substitution, as a real deployment would read it.
        Files.writeString(file, """
                llm {
                  IT {
                    provider   = ollama
                    base-url   = ${OLLAMA_BASE_URL}
                    model-name = "llama3.2"
                    model-name = ${?OLLAMA_MODEL}
                    timeout    = 120s
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
