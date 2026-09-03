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
package io.github.maxtrezzi.modelrack4j.provider.glm;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.exception.LangChain4jException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Characterises {@code langchain4j-community-zhipu-ai}, not this library. It pins the reason
 * {@link GlmProviderFactory#validate} rejects a key shape at all: the provider does not send
 * the key, it splits the key and signs a token with the second half, so a key of the wrong
 * shape fails before the request leaves the process — and fails with exceptions that are not
 * in the {@link LangChain4jException} family the manual tells applications to catch.
 *
 * <p>If a later version of the module starts reporting these properly, these tests fail, and
 * that is the point: nothing else would tell us the check here had become redundant.
 *
 * <p>Every model here points at a closed local port, so no test reaches a network. The last
 * one is the control: a key of the right shape gets as far as opening a socket, which is what
 * makes "the others failed earlier" a measurement rather than an assumption.
 */
class ZhipuAiKeyHandlingTest {

    /** Port 1 is reserved and never listening, so a connection attempt is refused at once. */
    private static final String CLOSED_PORT = "http://127.0.0.1:1/";

    @Test
    @DisplayName("a key with no '.' throws ArrayIndexOutOfBoundsException while signing")
    void keyWithoutADotFailsWithAJdkException() {
        assertThatThrownBy(() -> chatWith("no-dot-in-here-at-all"))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class)
                .isNotInstanceOf(LangChain4jException.class)
                .hasStackTraceContaining("AuthorizationUtils.generateToken");
    }

    @Test
    @DisplayName("a secret under 16 bytes throws from the JWT library, naming HS256")
    void shortSecretFailsInTheJwtLibrary() {
        // Not the same failure as above, and the entry for P25 knew only about that one. The
        // type is io.jsonwebtoken.security.SignatureException, asserted by name so that this
        // module does not take a compile dependency on the JWT library to say so.
        assertThatThrownBy(() -> chatWith("e7c1a2b3.short"))
                .isNotInstanceOf(LangChain4jException.class)
                .hasStackTraceContaining("io.jsonwebtoken")
                .hasMessageContaining("HS256");
    }

    @Test
    @DisplayName("a key of the right shape gets as far as the socket")
    void wellShapedKeyReachesTheNetwork() {
        // The control. This one fails because nothing is listening on port 1, which places
        // the two failures above strictly before any connection attempt.
        assertThatThrownBy(() -> chatWith("e7c1a2b3.0123456789abcdef"))
                .hasStackTraceContaining("ConnectException");
    }

    private static void chatWith(String apiKey) {
        ZhipuAiChatModel.builder()
                .baseUrl(CLOSED_PORT)
                .apiKey(apiKey)
                .model("glm-4.6")
                .maxRetries(1)
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(2))
                .build()
                .chat("hi");
    }
}
