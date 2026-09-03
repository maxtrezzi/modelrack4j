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
package io.github.maxtrezzi.modelrack4j.spi;

import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.moderation.ModerationModel;
import io.github.maxtrezzi.modelrack4j.ConfigValidationException;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import java.util.Optional;

/**
 * Builds LangChain4j model objects for one provider.
 *
 * <p>One implementation per provider, each in its own module, discovered with
 * {@link java.util.ServiceLoader} from
 * {@code META-INF/services/io.github.maxtrezzi.modelrack4j.spi.ProviderFactory}. Core depends
 * on no provider artifact, so the set of usable providers is exactly the set of provider
 * modules on the classpath.
 *
 * <p>Implementations must be stateless and safe for concurrent use: one instance serves every
 * configuration naming its provider.
 */
public interface ProviderFactory {

    /**
     * Returns the value this factory matches against a block's {@code provider} key.
     *
     * @return a stable id such as {@code "openai"}, never null or blank
     */
    String providerId();

    /**
     * Returns how this provider counts tokens, which decides whether token-window memory is
     * possible and whether it needs an explicit opt-in.
     *
     * @return the estimation class, never null
     */
    TokenEstimation tokenEstimation();

    /**
     * Returns whether this provider can build a {@code ModerationModel}, which decides
     * whether a block may set {@code moderation.enabled = true}.
     *
     * <p>Of the four providers in v1, only OpenAI can. Reporting it here rather than
     * rejecting it in {@link #validate(LlmConfig)} keeps the rule and its message in core,
     * beside the {@link #tokenEstimation()} rules, so every provider refuses the same
     * configuration in the same words.
     *
     * @return {@code true} if {@link #createModerationModel(LlmConfig)} can produce a model
     * @implSpec The default is {@code true}, which is not a claim that most providers
     *     moderate — it is what keeps a factory written against an earlier version working
     *     unchanged. Such a factory does not override this, so core lets the configuration
     *     through and the missing model is still caught a moment later, when
     *     {@link #createModerationModel(LlmConfig)} returns empty. Overriding it changes
     *     only which of the two messages the user sees, and how early.
     */
    default boolean supportsModeration() {
        return true;
    }

    /**
     * Checks anything this provider cannot support, beyond what the config record already
     * validates — most often a capability the configuration asks for and the provider lacks.
     *
     * <p>Capability rules that depend only on {@link #tokenEstimation()} or
     * {@link #supportsModeration()} are applied by core and must not be restated here.
     *
     * @param config the validated configuration naming this provider
     * @throws ConfigValidationException if this provider cannot honour the configuration
     */
    void validate(LlmConfig config);

    /**
     * Builds the chat model. Every bundle has one, so this never returns empty.
     *
     * @param config the validated configuration
     * @return the chat model, never null
     */
    ChatModel createChatModel(LlmConfig config);

    /**
     * Builds the streaming chat model, if the configuration asked for one.
     *
     * @param config the validated configuration
     * @return the streaming model, or empty when {@code streaming} is false
     */
    Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config);

    /**
     * Builds the moderation model, if the configuration asked for one and this provider has
     * one.
     *
     * @param config the validated configuration
     * @return the moderation model, or empty
     */
    Optional<ModerationModel> createModerationModel(LlmConfig config);

    /**
     * Builds the token count estimator that token-window memory needs.
     *
     * @param config the validated configuration
     * @return the estimator, or empty when {@link #tokenEstimation()} is
     *     {@link TokenEstimation#ABSENT}
     */
    Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config);
}
