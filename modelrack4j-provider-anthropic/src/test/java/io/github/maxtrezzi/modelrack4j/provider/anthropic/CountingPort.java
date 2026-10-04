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
package io.github.maxtrezzi.modelrack4j.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * A local port that accepts every connection, counts it and closes it at once.
 *
 * <p>For checking that a {@code base-url} reaches a model: a closed port would show that a
 * call failed, but not where it went, and a call that went to the vendor instead fails too.
 * A connection counted here is a request that arrived at the configured address. Closing it
 * straight away makes the call fail quickly, so no test waits for a timeout.
 */
final class CountingPort implements AutoCloseable {

    private static final String LOOPBACK = "127.0.0.1";

    private final ServerSocket socket;
    private final AtomicInteger accepted = new AtomicInteger();

    CountingPort() throws IOException {
        // 127.0.0.1 by name rather than getLoopbackAddress(), which is ::1 on a JVM that
        // prefers IPv6: the listener and the URL below must name the same address.
        socket = new ServerSocket(0, 50, InetAddress.getByName(LOOPBACK));
        Thread acceptor = new Thread(this::acceptUntilClosed, "counting-port");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /** @return the address to configure as {@code base-url} */
    String url() {
        return "http://" + LOOPBACK + ":" + socket.getLocalPort() + "/";
    }

    /**
     * Runs a call that is expected to fail, and asserts that it connected here first.
     *
     * @param what the model being checked, for the failure message
     * @param call a call that makes one request
     */
    void assertReachedBy(String what, ThrowingCallable call) {
        int before = accepted.get();
        // The call fails, because the connection is closed with no answer. Which exception
        // that is depends on the client and does not matter: the count is the measurement.
        catchThrowable(call);
        assertThat(accepted.get()).as("%s did not connect to %s", what, url())
                .isGreaterThan(before);
    }

    /**
     * Makes one streaming request and waits for it to end, however it ends.
     *
     * @param model the model to call
     * @throws Exception if it has not ended within 30 seconds
     */
    static void streamOnce(StreamingChatModel model) throws Exception {
        CompletableFuture<Void> ended = new CompletableFuture<>();
        try {
            model.chat("hi", new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    ended.complete(null);
                }

                @Override
                public void onError(Throwable error) {
                    ended.complete(null);
                }
            });
        } catch (RuntimeException thrownBeforeTheHandler) {
            ended.complete(null);
        }
        ended.get(30, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private void acceptUntilClosed() {
        while (!socket.isClosed()) {
            // Counted before the close, so a caller that has seen its call fail always sees
            // the count already raised.
            try (Socket ignored = socket.accept()) {
                accepted.incrementAndGet();
            } catch (IOException closedOrFailed) {
                return;
            }
        }
    }
}
