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
package io.github.maxtrezzi.modelrack4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.moderation.listener.ModerationModelRequestContext;
import io.github.maxtrezzi.modelrack4j.testing.FakeCountingProviderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Listeners on the models the registry builds (ADR-0063): registered once on the builder,
 * given to every model at the first load and at every reload, and checked against what the
 * model reports.
 */
class ModelListenersTest {

    @TempDir
    Path dir;

    /** Two blocks on the same provider and model, so only the name tells them apart. */
    private static final String TWO_BLOCKS = """
            llm {
              SL { provider = fake-local, api-key = "k", model-name = "m", streaming = true }
              SH { provider = fake-local, api-key = "k", model-name = "m" }
            }
            """;

    /** Remembers which configuration each request came through. */
    private static final class Recorder implements ChatModelListener {

        private final String name;
        private final List<String> seen;

        Recorder(String name, List<String> seen) {
            this.name = name;
            this.seen = seen;
        }

        @Override
        public void onRequest(ChatModelRequestContext context) {
            seen.add(name + " request");
        }

        @Override
        public void onResponse(ChatModelResponseContext context) {
            seen.add(name + " response");
        }
    }

    /** A listener that fails on every call it is given. */
    private static final class Throwing implements ChatModelListener {

        @Override
        public void onRequest(ChatModelRequestContext context) {
            throw new IllegalStateException("a listener that throws");
        }

        @Override
        public void onResponse(ChatModelResponseContext context) {
            throw new IllegalStateException("a listener that throws");
        }
    }

    private Path write(String name, String hocon) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, hocon, StandardCharsets.UTF_8);
        return file;
    }

    /** Streams one request and returns once it completes, which the fake does at once. */
    private static void streamOnce(LlmBundle<?> bundle) {
        bundle.streamingChatModel().orElseThrow().chat("hi", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });
    }

    @Test
    @DisplayName("a listener sees the calls of the chat model and of the streaming model")
    void aListenerSeesChatAndStreaming() throws IOException {
        List<String> seen = Collections.synchronizedList(new ArrayList<>());

        try (LlmRegistry<Void> registry = LlmRegistry.builder()
                .configFiles(List.of(write("app.conf", TWO_BLOCKS)))
                .chatModelListeners(config -> List.of(new Recorder(config.name(), seen)))
                .build()) {

            registry.get("SL").chatModel().chat("hi");
            streamOnce(registry.get("SL"));

            assertThat(seen).containsExactly(
                    "SL request", "SL response", "SL request", "SL response");
        }
    }

    @Test
    @DisplayName("the function receives each configuration, so a listener can know its name")
    void theFunctionTellsTheBlocksApart() throws IOException {
        // The two blocks share a provider and a model name, so nothing LangChain4j hands a
        // listener can tell them apart. Only the function's argument can.
        List<String> seen = Collections.synchronizedList(new ArrayList<>());

        try (LlmRegistry<Void> registry = LlmRegistry.builder()
                .configFiles(List.of(write("app.conf", TWO_BLOCKS)))
                .chatModelListeners(config -> List.of(new Recorder(config.name(), seen)))
                .build()) {

            registry.get("SH").chatModel().chat("hi");
            registry.get("SL").chatModel().chat("hi");

            assertThat(seen).containsExactly(
                    "SH request", "SH response", "SL request", "SL response");
        }
    }

    @Test
    @DisplayName("a moderation listener sees the calls of the moderation model")
    void aModerationListenerSeesModeration() throws IOException {
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        ModerationModelListener listener = new ModerationModelListener() {
            @Override
            public void onRequest(ModerationModelRequestContext context) {
                seen.add(context.moderationRequest().texts().toString());
            }
        };

        try (LlmRegistry<Void> registry = LlmRegistry.builder()
                .configFiles(List.of(write("app.conf", """
                        llm.CR { provider = fake-local, api-key = "k", model-name = "m"
                                 moderation { enabled = true } }
                        """)))
                .moderationModelListeners(config -> List.of(listener))
                .build()) {

            registry.get("CR").moderationModel().orElseThrow().moderate("is this fine");

            assertThat(seen).containsExactly("[is this fine]");
        }
    }

    @Test
    @DisplayName("a reload rebuilds only the changed bundle, and the new models keep the listeners")
    void aReloadKeepsTheListeners() throws IOException {
        Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        Path file = write("app.conf", TWO_BLOCKS);

        try (LlmRegistry<Void> registry = LlmRegistry.builder()
                .configFiles(List.of(file))
                .chatModelListeners(config -> {
                    calls.computeIfAbsent(config.name(), name -> new AtomicInteger())
                            .incrementAndGet();
                    return List.of(new Recorder(config.name(), seen));
                })
                .build()) {
            var before = registry.get("SH").chatModel();

            Files.writeString(file, TWO_BLOCKS.replace(
                    "SH { provider = fake-local, api-key = \"k\", model-name = \"m\" }",
                    "SH { provider = fake-local, api-key = \"k\", model-name = \"m2\" }"),
                    StandardCharsets.UTF_8);
            assertThat(registry.reload()).hasValueSatisfying(
                    change -> assertThat(change.updated()).containsExactly("SH"));

            assertThat(registry.get("SH").chatModel()).isNotSameAs(before);
            registry.get("SH").chatModel().chat("hi");
            assertThat(seen).containsExactly("SH request", "SH response");
            // SL was carried over, so its listeners were not asked for again.
            assertThat(calls).containsOnlyKeys("SL", "SH");
            assertThat(calls.get("SL")).hasValue(1);
            assertThat(calls.get("SH")).hasValue(2);
        }
    }

    @Test
    @DisplayName("listeners change no configuration: reloading the same text reports nothing")
    void listenersAreNotPartOfTheDiff() throws IOException {
        // ADR-0006: the diff is record equality on LlmConfig. The listeners are not in the
        // record, and a new list from the function on every call must not look like a change.
        AtomicInteger calls = new AtomicInteger();

        try (LlmRegistry<Void> registry = LlmRegistry.builder()
                .configFiles(List.of(write("app.conf", TWO_BLOCKS)))
                .chatModelListeners(config -> {
                    calls.incrementAndGet();
                    return List.of(new Recorder(config.name(), new ArrayList<>()));
                })
                .build()) {

            assertThat(registry.reload()).isEmpty();
            assertThat(calls).hasValue(2);
        }
    }

    @Test
    @DisplayName("a listener that throws breaks neither a build, nor a reload, nor the call")
    void aThrowingListenerBreaksNothing() throws IOException {
        // LangChain4j calls listeners around a model call, never while a model is built, and
        // catches what they throw. This pins both halves, so a change in either is seen here.
        Path file = write("app.conf", TWO_BLOCKS);

        try (LlmRegistry<Void> registry = LlmRegistry.builder()
                .configFiles(List.of(file))
                .chatModelListeners(config -> List.of(new Throwing()))
                .build()) {

            Files.writeString(file, TWO_BLOCKS.replace("model-name = \"m\" }",
                    "model-name = \"m2\" }"), StandardCharsets.UTF_8);
            assertThat(registry.reload()).isPresent();

            assertThat(registry.get("SH").chatModel().chat("hi")).isEqualTo("ok");
        }
    }

    @Test
    @DisplayName("listeners registered before the custom-properties handler are kept")
    void listenersSurviveTheHandlerRetyping() throws IOException {
        // customPropertiesHandler changes the builder's type by casting this. A new Builder
        // there would drop every field already set, the listeners among them.
        List<String> seen = Collections.synchronizedList(new ArrayList<>());

        try (LlmRegistry<String> registry = LlmRegistry.builder()
                .configFiles(List.of(write("app.conf", TWO_BLOCKS)))
                .chatModelListeners(config -> List.of(new Recorder(config.name(), seen)))
                .customPropertiesHandler(LlmConfig::customPropertiesText)
                .build()) {

            registry.get("SH").chatModel().chat("hi");

            assertThat(seen).containsExactly("SH request", "SH response");
        }
    }

    @Test
    @DisplayName("a factory that ignores the listeners is refused, rather than dropping them")
    void aFactoryThatDropsListenersIsRefused() throws IOException {
        // fake-legacy implements only the one-argument create methods, as a factory written
        // before 0.4.0 does, so the SPI's default ignores the context.
        Path file = write("app.conf",
                "llm.SL { provider = fake-legacy, api-key = \"k\", model-name = \"m\" }\n");

        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file))
                .chatModelListeners(config -> List.of(new Throwing()))
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessage("llm.SL: provider 'fake-legacy' built a chat model without the"
                        + " listeners this registry was given. Its factory does not pass them"
                        + " on, probably because it was built for a modelrack4j older than"
                        + " 0.4.0. Rebuild it against this version, or register no chat model"
                        + " listeners.");
    }

    @Test
    @DisplayName("the same factory still builds when no listener is registered")
    void aFactoryThatDropsListenersStillBuildsWithoutThem() throws IOException {
        Path file = write("app.conf",
                "llm.SL { provider = fake-legacy, api-key = \"k\", model-name = \"m\" }\n");

        try (LlmRegistry<Void> registry = LlmRegistry.builder()
                .configFiles(List.of(file))
                .chatModelListeners(config -> List.of())
                .build()) {

            assertThat(registry.get("SL").chatModel().listeners()).isEmpty();
        }
    }

    @Test
    @DisplayName("a streaming model without the listeners is refused, though the chat model has them")
    void aStreamingModelWithoutListenersIsRefused() throws IOException {
        Path file = write("app.conf", """
                llm.SL { provider = fake-partial, api-key = "k", model-name = "m", streaming = true }
                """);

        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file))
                .chatModelListeners(config -> List.of(new Throwing()))
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageStartingWith("llm.SL: provider 'fake-partial' built a streaming chat"
                        + " model without the listeners");
    }

    @Test
    @DisplayName("a moderation model without its listeners is refused")
    void aModerationModelWithoutListenersIsRefused() throws IOException {
        Path file = write("app.conf", """
                llm.CR { provider = fake-partial, api-key = "k", model-name = "m"
                         moderation { enabled = true } }
                """);
        ModerationModelListener listener = new ModerationModelListener() {
        };

        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file))
                .moderationModelListeners(config -> List.of(listener))
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageStartingWith("llm.CR: provider 'fake-partial' built a moderation"
                        + " model without the listeners");
    }

    @Test
    @DisplayName("a factory written before ModelContext still builds every model it offers")
    void theDefaultsDelegateToTheOneArgumentMethods() throws IOException {
        // The two-argument defaults must call the old methods, not return nothing: a default
        // that returned empty would fail this block with "produced no streaming chat model".
        Path file = write("app.conf", """
                llm.SL { provider = fake-pre-context, api-key = "k", model-name = "m"
                         streaming = true, moderation { enabled = true } }
                """);

        try (LlmRegistry<Void> registry =
                LlmRegistry.builder().configFiles(List.of(file)).build()) {

            assertThat(registry.get("SL").streamingChatModel()).isPresent();
            assertThat(registry.get("SL").moderationModel()).isPresent();
        }
    }

    @Test
    @DisplayName("a listener function that throws is refused, naming the configuration")
    void aThrowingFunctionIsRefused() throws IOException {
        Path file = write("app.conf", TWO_BLOCKS);

        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file))
                .chatModelListeners(config -> {
                    throw new IllegalStateException("no metrics registry yet");
                })
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessage("llm.SH: the chat model listener function failed: no metrics"
                        + " registry yet")
                .hasCauseInstanceOf(IllegalStateException.class);
        // With no message of its own, the exception's class is what names the failure.
        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file))
                .chatModelListeners(config -> {
                    throw new IllegalStateException();
                })
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessage("llm.SH: the chat model listener function failed:"
                        + " java.lang.IllegalStateException");
    }

    @Test
    @DisplayName("a listener function that returns null, or a null element, is refused")
    void aNullFromTheFunctionIsRefused() throws IOException {
        Path file = write("app.conf", TWO_BLOCKS);
        List<ChatModelListener> holdingNull = new ArrayList<>();
        holdingNull.add(null);

        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file))
                .chatModelListeners(config -> null)
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessage("llm.SH: the chat model listener function returned null. Return an"
                        + " empty list for no listeners.");
        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file))
                .moderationModelListeners(config -> new ArrayList<>(Collections.nCopies(1, null)))
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessage("llm.SH: the moderation model listener function returned a list"
                        + " holding null. Every element must be a listener.");
        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file))
                .chatModelListeners(config -> holdingNull)
                .build())
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("returned a list holding null");
    }

    @Test
    @DisplayName("a rejected reload leaves the previous bundles live, with their listeners")
    void aRejectedReloadKeepsThePreviousListeners() throws IOException {
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger builds = new AtomicInteger();
        Path file = write("app.conf", TWO_BLOCKS);

        try (LlmRegistry<Void> registry = LlmRegistry.builder()
                .configFiles(List.of(file))
                .chatModelListeners(config -> {
                    if (builds.incrementAndGet() > 2) {
                        throw new IllegalStateException("refused on the reload");
                    }
                    return List.of(new Recorder(config.name(), seen));
                })
                .build()) {

            Files.writeString(file, TWO_BLOCKS.replace("model-name = \"m\" }",
                    "model-name = \"m2\" }"), StandardCharsets.UTF_8);
            assertThatThrownBy(registry::reload).isInstanceOf(ConfigValidationException.class);

            registry.get("SH").chatModel().chat("hi");
            assertThat(registry.get("SH").config().modelName()).isEqualTo("m");
            assertThat(seen).containsExactly("SH request", "SH response");
        }
    }

    @Test
    @DisplayName("the custom-properties handler runs before any model is built")
    void theHandlerRunsBeforeTheModels() throws IOException {
        // The handler's implNote and AGENTS.md both said so while buildBundle called it as the
        // bundle's last constructor argument, after every model was built.
        FakeCountingProviderFactory.CHAT_MODELS_BUILT.set(0);
        Path file = write("app.conf",
                "llm.SL { provider = fake-counting, api-key = \"k\", model-name = \"m\" }\n");

        assertThatThrownBy(() -> LlmRegistry.builder()
                .configFiles(List.of(file))
                .customPropertiesHandler(config -> {
                    throw new IllegalArgumentException("not for this application");
                })
                .build())
                .isInstanceOf(ConfigValidationException.class);
        assertThat(FakeCountingProviderFactory.CHAT_MODELS_BUILT).hasValue(0);
    }
}
